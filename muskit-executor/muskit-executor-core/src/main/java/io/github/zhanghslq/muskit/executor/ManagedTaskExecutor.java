package io.github.zhanghslq.muskit.executor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.lifecycle.DrainController;
import io.github.zhanghslq.muskit.lifecycle.DrainPermit;
import io.github.zhanghslq.muskit.lifecycle.DrainSnapshot;
import io.github.zhanghslq.muskit.lifecycle.Drainable;
import io.github.zhanghslq.muskit.observation.MuskitMetric;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.ObservationTags;
import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;

/**
 * 有界接收任务并传播 Muskit Context 与 Deadline 的平台线程或虚拟线程执行器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ManagedTaskExecutor implements Executor, Drainable, AutoCloseable {

    private final ManagedExecutorConfig config;
    private final ExecutorService delegate;
    private final Semaphore capacity;
    private final Semaphore executionPermits;
    private final DrainController drainController;
    private final MuskitObservationRegistry observationRegistry;
    private final ObservationTags metricTags;
    private final AtomicLong inflight = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 使用空观测注册器创建受管执行器。
     *
     * @param config 执行器配置
     */
    public ManagedTaskExecutor(ManagedExecutorConfig config) {
        this(config, MuskitObservationRegistry.noop());
    }

    /**
     * 创建带统一可观测性的受管执行器。
     *
     * @param config 执行器配置
     * @param observationRegistry 统一观测注册器
     */
    public ManagedTaskExecutor(
            ManagedExecutorConfig config,
            MuskitObservationRegistry observationRegistry) {
        this.config = Objects.requireNonNull(config, "执行器配置不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        this.capacity = new Semaphore(config.maxConcurrency() + config.queueCapacity(), true);
        this.executionPermits = new Semaphore(config.maxConcurrency(), true);
        this.drainController = new DrainController("executor-" + config.name());
        this.metricTags = ObservationTags.of(MuskitTagKey.EXECUTOR, config.name());
        this.delegate = createDelegate(config);
    }

    /**
     * 提交无返回值任务；提交阶段拒绝会同步抛出异常。
     *
     * @param command 待执行任务
     */
    @Override
    public void execute(Runnable command) {
        runAsync(command);
    }

    /**
     * 提交无返回值任务并返回可观察完成状态。
     *
     * @param task 待执行任务
     * @return 异步完成状态
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "执行器任务不能为空");
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /**
     * 提交有返回值任务，并捕获调用线程的业务上下文与 Deadline。
     *
     * @param task 待执行任务
     * @param <T> 结果类型
     * @return 异步结果
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "执行器任务不能为空");
        ensureOpen();
        DrainPermit drainPermit = drainController.tryEnter()
                .orElseThrow(() -> rejected(TaskRejectionReason.DRAINING));
        if (!capacity.tryAcquire()) {
            drainPermit.close();
            throw rejected(TaskRejectionReason.CAPACITY);
        }

        MuskitContext capturedContext = MuskitContextHolder.currentOrEmpty();
        Deadline capturedDeadline = DeadlineContext.current().orElse(null);
        CompletableFuture<T> result = new CompletableFuture<>();
        long currentInflight = inflight.incrementAndGet();
        try {
            observationRegistry.setGauge(MuskitMetric.EXECUTOR_INFLIGHT, currentInflight, metricTags);
            delegate.execute(() -> runTask(task, capturedContext, capturedDeadline, result, drainPermit));
        } catch (RuntimeException failure) {
            cleanupAcceptedTask(drainPermit);
            if (failure instanceof RejectedExecutionException) {
                throw rejected(closed.get() ? TaskRejectionReason.CLOSED : TaskRejectionReason.CAPACITY);
            }
            throw failure;
        }
        return result;
    }

    /**
     * 返回执行器名称。
     *
     * @return 低基数执行器名称
     */
    @Override
    public String name() {
        return config.name();
    }

    /**
     * 停止接受新任务。
     */
    @Override
    public void beginDrain() {
        drainController.beginDrain();
    }

    /**
     * 等待已接收任务真实完成。
     *
     * @param timeout 最大等待时间
     * @return 是否完成排空
     */
    @Override
    public boolean awaitDrained(Duration timeout) {
        return drainController.awaitDrained(timeout);
    }

    /**
     * 返回不包含任务参数和上下文值的执行器状态快照。
     *
     * @return 执行器快照
     */
    public ManagedExecutorSnapshot snapshot() {
        DrainSnapshot drainSnapshot = drainController.snapshot();
        return new ManagedExecutorSnapshot(
                config.name(),
                config.type(),
                drainSnapshot.state(),
                drainSnapshot.inflight(),
                capacity.availablePermits());
    }

    /**
     * 停止接收任务并在配置期限内排空，超时后中断剩余任务。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        beginDrain();
        boolean completed;
        try {
            completed = awaitDrained(config.shutdownTimeout());
        } catch (RuntimeException failure) {
            delegate.shutdownNow();
            throw failure;
        }
        delegate.shutdown();
        if (!completed || !awaitTermination(config.shutdownTimeout())) {
            delegate.shutdownNow();
        }
    }

    /**
     * 在捕获的上下文作用域内运行任务并确保所有许可最终释放。
     *
     * @param task 业务任务
     * @param capturedContext 捕获的业务上下文
     * @param capturedDeadline 捕获的 Deadline，可为空
     * @param result 异步结果
     * @param drainPermit 排空许可
     * @param <T> 结果类型
     */
    private <T> void runTask(
            Callable<T> task,
            MuskitContext capturedContext,
            Deadline capturedDeadline,
            CompletableFuture<T> result,
            DrainPermit drainPermit) {
        boolean executionAcquired = false;
        String outcome = "failed";
        try {
            executionPermits.acquire();
            executionAcquired = true;
            T value;
            try (MuskitContextHolder.Scope ignored = MuskitContextHolder.open(capturedContext)) {
                if (capturedDeadline == null) {
                    value = task.call();
                } else {
                    try (DeadlineContext.Scope deadlineScope = DeadlineContext.open(capturedDeadline)) {
                        value = task.call();
                    }
                }
            }
            outcome = "completed";
            result.complete(value);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(interrupted);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        } finally {
            if (executionAcquired) {
                executionPermits.release();
            }
            try {
                observationRegistry.increment(
                        MuskitMetric.EXECUTOR_TASK,
                        metricTags.and(MuskitTagKey.OUTCOME, outcome));
            } finally {
                cleanupAcceptedTask(drainPermit);
            }
        }
    }

    /**
     * 释放容量、排空许可并更新在途仪表值。
     *
     * @param drainPermit 排空许可
     */
    private void cleanupAcceptedTask(DrainPermit drainPermit) {
        capacity.release();
        drainPermit.close();
        long remaining = inflight.updateAndGet(previous -> Math.max(0L, previous - 1L));
        observationRegistry.setGauge(MuskitMetric.EXECUTOR_INFLIGHT, remaining, metricTags);
    }

    /**
     * 校验执行器尚未关闭。
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw rejected(TaskRejectionReason.CLOSED);
        }
    }

    /**
     * 创建任务拒绝异常并记录低基数原因。
     *
     * @param reason 拒绝原因
     * @return 任务拒绝异常
     */
    private ManagedTaskRejectedException rejected(TaskRejectionReason reason) {
        observationRegistry.increment(
                MuskitMetric.EXECUTOR_TASK,
                metricTags.and(MuskitTagKey.OUTCOME, "rejected-" + reason.name().toLowerCase()));
        return new ManagedTaskRejectedException(config.name(), reason);
    }

    /**
     * 创建平台线程或虚拟线程委托执行器。
     *
     * @param config 执行器配置
     * @return JDK 执行器
     */
    private ExecutorService createDelegate(ManagedExecutorConfig config) {
        String prefix = "muskit-" + config.name() + "-";
        if (config.type() == ExecutorType.VIRTUAL) {
            return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(prefix, 0L).factory());
        }
        return Executors.newFixedThreadPool(
                config.maxConcurrency(),
                Thread.ofPlatform().name(prefix, 0L).factory());
    }

    /**
     * 使用可中断等待关闭委托执行器，并恢复中断标记。
     *
     * @param timeout 最大等待时间
     * @return 是否终止
     */
    private boolean awaitTermination(Duration timeout) {
        try {
            return delegate.awaitTermination(saturatedNanos(timeout), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 将时间转换为饱和纳秒数。
     *
     * @param timeout 等待时间
     * @return 纳秒值
     */
    private long saturatedNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
