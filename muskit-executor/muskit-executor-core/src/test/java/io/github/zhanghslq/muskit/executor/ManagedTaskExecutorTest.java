package io.github.zhanghslq.muskit.executor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受管执行器上下文传播、容量和排空测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class ManagedTaskExecutorTest {

    /**
     * 验证虚拟线程执行并传播业务上下文和 Deadline，工作线程结束后不泄漏上下文。
     *
     * @throws Exception 异步等待异常
     */
    @Test
    void shouldPropagateContextAndDeadlineToVirtualThread() throws Exception {
        ManagedExecutorConfig config = new ManagedExecutorConfig(
                "virtual", ExecutorType.VIRTUAL, 2, 1, Duration.ofSeconds(1));
        try (ManagedTaskExecutor executor = new ManagedTaskExecutor(config);
                MuskitContextHolder.Scope ignored = MuskitContextHolder.open(
                        MuskitContext.of(Map.of("tenant", "t1")));
                DeadlineContext.Scope deadlineScope = DeadlineContext.open(Deadline.after(Duration.ofSeconds(1)))) {
            String result = executor.submit(() -> String.join(
                    ":",
                    Boolean.toString(Thread.currentThread().isVirtual()),
                    MuskitContextHolder.currentOrEmpty().get("tenant").orElseThrow(),
                    Boolean.toString(DeadlineContext.current().isPresent())))
                    .get(1, TimeUnit.SECONDS);

            assertThat(result).isEqualTo("true:t1:true");
        }
    }

    /**
     * 验证运行和等待容量耗尽时同步拒绝新任务。
     *
     * @throws Exception 协调任务异常
     */
    @Test
    void shouldRejectWhenCapacityIsExhausted() throws Exception {
        ManagedExecutorConfig config = new ManagedExecutorConfig(
                "bounded", ExecutorType.PLATFORM, 1, 0, Duration.ofSeconds(1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ManagedTaskExecutor executor = new ManagedTaskExecutor(config)) {
            var first = executor.runAsync(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executor.runAsync(() -> { }))
                    .isInstanceOfSatisfying(ManagedTaskRejectedException.class,
                            failure -> assertThat(failure.reason()).isEqualTo(TaskRejectionReason.CAPACITY));
            release.countDown();
            first.get(1, TimeUnit.SECONDS);
        }
    }

    /**
     * 验证开始排空后拒绝新任务，并等待已经接收的任务完成。
     *
     * @throws Exception 协调任务异常
     */
    @Test
    void shouldDrainAcceptedTasksAndRejectNewOnes() throws Exception {
        ManagedExecutorConfig config = new ManagedExecutorConfig(
                "drain", ExecutorType.VIRTUAL, 1, 1, Duration.ofSeconds(1));
        CountDownLatch release = new CountDownLatch(1);
        try (ManagedTaskExecutor executor = new ManagedTaskExecutor(config)) {
            var first = executor.runAsync(() -> {
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            executor.beginDrain();

            assertThatThrownBy(() -> executor.runAsync(() -> { }))
                    .isInstanceOfSatisfying(ManagedTaskRejectedException.class,
                            failure -> assertThat(failure.reason()).isEqualTo(TaskRejectionReason.DRAINING));
            assertThat(executor.awaitDrained(Duration.ZERO)).isFalse();
            release.countDown();
            first.get(1, TimeUnit.SECONDS);
            assertThat(executor.awaitDrained(Duration.ofSeconds(1))).isTrue();
        }
    }
}
