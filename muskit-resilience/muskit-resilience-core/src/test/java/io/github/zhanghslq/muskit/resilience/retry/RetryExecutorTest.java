package io.github.zhanghslq.muskit.resilience.retry;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deadline 感知重试器测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RetryExecutorTest {

    /**
     * 验证同步调用在瞬时失败后按最大调用次数恢复成功。
     *
     * @throws Throwable 重试执行异常
     */
    @Test
    void shouldRetrySynchronousFailureUntilSuccess() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        try (RetryExecutor executor = new RetryExecutor()) {
            String result = executor.execute(policy(3, Duration.ZERO), () -> {
                if (calls.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary");
                }
                return "ok";
            });

            assertThat(result).isEqualTo("ok");
            assertThat(calls).hasValue(3);
        }
    }

    /**
     * 验证 abortOn 异常立即失败且不继续调用。
     */
    @Test
    void shouldAbortExcludedFailureImmediately() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy policy = new RetryPolicy(
                "abort", 3, Duration.ZERO, 2D, Duration.ZERO, 0D,
                Set.of(Exception.class), Set.of(IllegalArgumentException.class), RetryPredicate.always());
        try (RetryExecutor executor = new RetryExecutor()) {
            assertThatThrownBy(() -> executor.execute(policy, () -> {
                calls.incrementAndGet();
                throw new IllegalArgumentException("invalid");
            })).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(calls).hasValue(1);
    }

    /**
     * 验证异步失败通过调度器重试且最终返回成功结果。
     */
    @Test
    void shouldRetryCompletionStageWithoutBlockingCaller() {
        AtomicInteger calls = new AtomicInteger();
        try (RetryExecutor executor = new RetryExecutor()) {
            CompletionStage<String> result = executor.executeAsync(policy(3, Duration.ZERO), () -> {
                if (calls.incrementAndGet() < 2) {
                    return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
                }
                return CompletableFuture.completedFuture("ok");
            });

            assertThat(result.toCompletableFuture()).isCompletedWithValue("ok");
            assertThat(calls).hasValue(2);
        }
    }

    /**
     * 验证当前 Deadline 无法容纳下一次退避时不再调用业务。
     */
    @Test
    void shouldStopRetryWhenDeadlineCannotFitBackoff() {
        AtomicInteger calls = new AtomicInteger();
        try (RetryExecutor executor = new RetryExecutor();
                DeadlineContext.Scope ignored = DeadlineContext.open(Deadline.after(Duration.ofMillis(50)))) {
            assertThatThrownBy(() -> executor.execute(policy(3, Duration.ofMillis(100)), () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("temporary");
            })).isInstanceOf(DeadlineExceededException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
        assertThat(calls).hasValue(1);
    }

    /**
     * 验证同步退避中断会恢复线程中断标记。
     */
    @Test
    void shouldRestoreInterruptFlagWhenBackoffIsInterrupted() {
        try (RetryExecutor executor = new RetryExecutor()) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> executor.execute(policy(2, Duration.ofSeconds(1)), () -> {
                throw new IllegalStateException("temporary");
            })).isInstanceOf(RetryInterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 创建测试重试策略。
     *
     * @param maxAttempts 最大调用次数
     * @param delay 固定等待时间
     * @return 重试策略
     */
    private RetryPolicy policy(int maxAttempts, Duration delay) {
        return new RetryPolicy(
                "remote-call",
                maxAttempts,
                delay,
                1D,
                delay,
                0D,
                Set.of(Exception.class),
                Set.of(),
                RetryPredicate.always());
    }
}
