package io.github.zhanghslq.muskit.resilience.retry;

import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineExceededException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

/**
 * 执行同步和 CompletionStage 异步调用的 Deadline 感知重试器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RetryExecutor implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private final DoubleSupplier random;
    private final boolean ownsScheduler;
    private final MuskitObservationRegistry observationRegistry;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 使用单个守护调度线程和线程局部随机数创建重试器。
     */
    public RetryExecutor() {
        this(MuskitObservationRegistry.noop());
    }

    /**
     * 使用单个守护调度线程创建带统一可观测性的重试器。
     *
     * @param observationRegistry 统一观测注册器
     */
    public RetryExecutor(MuskitObservationRegistry observationRegistry) {
        this(
                createDefaultScheduler(),
                () -> ThreadLocalRandom.current().nextDouble(),
                true,
                observationRegistry);
    }

    /**
     * 使用指定调度器和随机数源创建测试或自定义重试器。
     *
     * @param scheduler 异步退避调度器
     * @param random 返回零到一之间数值的随机数源
     */
    public RetryExecutor(ScheduledExecutorService scheduler, DoubleSupplier random) {
        this(scheduler, random, false, MuskitObservationRegistry.noop());
    }

    /**
     * 使用指定调度器、随机源和统一观测注册器创建重试器。
     *
     * @param scheduler 异步退避调度器
     * @param random 返回零到一之间数值的随机数源
     * @param observationRegistry 统一观测注册器
     */
    public RetryExecutor(
            ScheduledExecutorService scheduler,
            DoubleSupplier random,
            MuskitObservationRegistry observationRegistry) {
        this(scheduler, random, false, observationRegistry);
    }

    /**
     * 创建重试器并记录调度器所有权。
     *
     * @param scheduler 异步退避调度器
     * @param random 随机数源
     * @param ownsScheduler 关闭时是否关闭调度器
     * @param observationRegistry 统一观测注册器
     */
    private RetryExecutor(
            ScheduledExecutorService scheduler,
            DoubleSupplier random,
            boolean ownsScheduler,
            MuskitObservationRegistry observationRegistry) {
        this.scheduler = Objects.requireNonNull(scheduler, "重试调度器不能为空");
        this.random = Objects.requireNonNull(random, "重试随机数源不能为空");
        this.ownsScheduler = ownsScheduler;
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
    }

    /**
     * 按策略执行同步调用，最终失败保持原异常类型。
     *
     * @param policy 重试策略
     * @param invocation 可重复调用的业务动作
     * @param <T> 结果类型
     * @return 调用结果
     * @throws Throwable 最终业务异常
     */
    public <T> T execute(RetryPolicy policy, RetryInvocation<T> invocation) throws Throwable {
        Objects.requireNonNull(policy, "重试策略不能为空");
        Objects.requireNonNull(invocation, "重试调用不能为空");
        ensureOpen();
        for (int attempt = 1; ; attempt++) {
            DeadlineContext.check();
            recordAttempt(policy);
            try {
                return invocation.invoke();
            } catch (Throwable failure) {
                boolean retryable = policy.shouldRetry(failure);
                if (attempt >= policy.maxAttempts() || !retryable) {
                    if (attempt >= policy.maxAttempts() && retryable) {
                        recordExhausted(policy);
                    }
                    throw failure;
                }
                Duration delay = delayBeforeAttempt(policy, attempt + 1);
                ensureDeadline(DeadlineContext.current().orElse(null), delay, failure);
                sleep(policy.name(), delay);
            }
        }
    }

    /**
     * 按策略执行异步调用，退避不阻塞调用线程并传播当前 Deadline。
     *
     * @param policy 重试策略
     * @param invocation 可重复创建异步任务的业务动作
     * @param <T> 结果类型
     * @return 最终异步结果
     */
    public <T> CompletionStage<T> executeAsync(
            RetryPolicy policy,
            RetryInvocation<? extends CompletionStage<T>> invocation) {
        Objects.requireNonNull(policy, "重试策略不能为空");
        Objects.requireNonNull(invocation, "异步重试调用不能为空");
        ensureOpen();
        AsyncRetry<T> state = new AsyncRetry<>(
                policy, invocation, DeadlineContext.current().orElse(null));
        state.attempt(1);
        return state.result;
    }

    /**
     * 关闭默认拥有的异步退避调度器，重复关闭保持幂等。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ownsScheduler) {
            scheduler.shutdownNow();
        }
    }

    /**
     * 校验重试器尚未关闭。
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("重试器已经关闭");
        }
    }

    /**
     * 创建默认的单线程守护调度器。
     *
     * @return 重试退避调度器
     */
    private static ScheduledExecutorService createDefaultScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "muskit-retry-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 记录一次真实业务调用尝试。
     *
     * @param policy 重试策略
     */
    private void recordAttempt(RetryPolicy policy) {
        observationRegistry.increment(
                MuskitMetric.RETRY_ATTEMPT,
                ObservationTags.of(MuskitTagKey.POLICY, policy.name())
                        .and(MuskitTagKey.OUTCOME, "started"));
    }

    /**
     * 记录达到最大尝试次数仍失败的调用。
     *
     * @param policy 重试策略
     */
    private void recordExhausted(RetryPolicy policy) {
        observationRegistry.increment(
                MuskitMetric.RETRY_EXHAUSTED,
                ObservationTags.of(MuskitTagKey.POLICY, policy.name())
                        .and(MuskitTagKey.OUTCOME, "exhausted"));
    }

    /**
     * 根据下一次调用序号计算指数退避和对称随机抖动。
     *
     * @param policy 重试策略
     * @param nextAttempt 下一次调用序号，从二开始
     * @return 退避等待时间
     */
    private Duration delayBeforeAttempt(RetryPolicy policy, int nextAttempt) {
        long initialNanos = saturatedNanos(policy.initialDelay());
        long maxNanos = saturatedNanos(policy.maxDelay());
        double exponential = initialNanos * Math.pow(policy.multiplier(), Math.max(0, nextAttempt - 2));
        double bounded = Math.min(maxNanos, exponential);
        double randomValue = random.getAsDouble();
        if (!Double.isFinite(randomValue) || randomValue < 0D || randomValue > 1D) {
            throw new IllegalStateException("重试随机数源必须返回 [0, 1] 范围内的有限数");
        }
        double factor = 1D - policy.jitter() + 2D * policy.jitter() * randomValue;
        long nanos = (long) Math.min(maxNanos, Math.ceil(bounded * factor));
        return Duration.ofNanos(Math.max(0L, nanos));
    }

    /**
     * 将 Duration 转换为饱和纳秒数，避免极大配置溢出。
     *
     * @param duration 时间长度
     * @return 饱和纳秒数
     */
    private long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 确保当前 Deadline 仍能容纳完整退避等待。
     *
     * @param deadline 当前 Deadline，可为空
     * @param delay 下一次退避等待时间
     * @param failure 最近一次业务失败
     */
    private void ensureDeadline(Deadline deadline, Duration delay, Throwable failure) {
        if (deadline == null) {
            return;
        }
        if (deadline.isExpired() || deadline.remaining().compareTo(delay) <= 0) {
            throw new DeadlineExceededException(failure);
        }
    }

    /**
     * 执行可中断同步退避并恢复中断标记。
     *
     * @param policyName 策略名称
     * @param delay 等待时间
     */
    private void sleep(String policyName, Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(saturatedNanos(delay));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException(policyName, exception);
        }
    }

    /**
     * 去除 CompletionStage 常见包装异常，保持业务异常类型。
     *
     * @param failure 异步完成异常
     * @return 原始业务异常
     */
    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 保存单次异步重试调用的状态和取消边界。
     *
     * @param <T> 结果类型
     * @author zhs
     * @since 2026-08-20
     */
    private final class AsyncRetry<T> {

        private final RetryPolicy policy;
        private final RetryInvocation<? extends CompletionStage<T>> invocation;
        private final Deadline deadline;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();

        /**
         * 创建异步重试状态。
         *
         * @param policy 重试策略
         * @param invocation 异步调用
         * @param deadline 捕获的 Deadline，可为空
         */
        private AsyncRetry(
                RetryPolicy policy,
                RetryInvocation<? extends CompletionStage<T>> invocation,
                Deadline deadline) {
            this.policy = policy;
            this.invocation = invocation;
            this.deadline = deadline;
            result.whenComplete((value, failure) -> {
                if (result.isCancelled()) {
                    ScheduledFuture<?> current = scheduled.get();
                    if (current != null) {
                        current.cancel(false);
                    }
                }
            });
        }

        /**
         * 在捕获的 Deadline 作用域内执行指定序号的一次异步调用。
         *
         * @param attempt 当前调用序号
         */
        private void attempt(int attempt) {
            if (result.isDone()) {
                return;
            }
            try {
                if (deadline != null && deadline.isExpired()) {
                    throw new DeadlineExceededException();
                }
                recordAttempt(policy);
                CompletionStage<T> stage;
                if (deadline == null) {
                    stage = invocation.invoke();
                } else {
                    try (DeadlineContext.Scope ignored = DeadlineContext.open(deadline)) {
                        stage = invocation.invoke();
                    }
                }
                Objects.requireNonNull(stage, "异步重试调用不能返回 null")
                        .whenComplete((value, failure) -> {
                            if (failure == null) {
                                result.complete(value);
                            } else {
                                handleFailure(attempt, unwrap(failure));
                            }
                        });
            } catch (Throwable failure) {
                handleFailure(attempt, unwrap(failure));
            }
        }

        /**
         * 根据策略完成最终异常或调度下一次调用。
         *
         * @param attempt 当前调用序号
         * @param failure 当前业务异常
         */
        private void handleFailure(int attempt, Throwable failure) {
            if (result.isDone()) {
                return;
            }
            boolean retryable = policy.shouldRetry(failure);
            if (attempt >= policy.maxAttempts() || !retryable) {
                if (attempt >= policy.maxAttempts() && retryable) {
                    recordExhausted(policy);
                }
                result.completeExceptionally(failure);
                return;
            }
            Duration delay = delayBeforeAttempt(policy, attempt + 1);
            try {
                ensureDeadline(deadline, delay, failure);
                ScheduledFuture<?> next = scheduler.schedule(
                        () -> attempt(attempt + 1),
                        saturatedNanos(delay),
                        TimeUnit.NANOSECONDS);
                scheduled.set(next);
                if (result.isCancelled()) {
                    next.cancel(false);
                }
            } catch (RuntimeException schedulingFailure) {
                result.completeExceptionally(schedulingFailure);
            }
        }
    }
}
