package io.github.zhanghslq.muskit.resilience.deadline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deadline 作用域和传播单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class DeadlineContextTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);

    /**
     * 验证子作用域不能延长父 Deadline，并在关闭后恢复父状态。
     */
    @Test
    void shouldUseEarliestDeadlineAndRestoreNestedScope() {
        Deadline parent = Deadline.after(Duration.ofSeconds(10), CLOCK);
        Deadline laterChild = Deadline.after(Duration.ofSeconds(20), CLOCK);
        Deadline earlierChild = Deadline.after(Duration.ofSeconds(5), CLOCK);

        try (DeadlineContext.Scope ignored = DeadlineContext.open(parent)) {
            try (DeadlineContext.Scope ignoredChild = DeadlineContext.open(laterChild)) {
                assertThat(DeadlineContext.current()).contains(parent);
            }
            try (DeadlineContext.Scope ignoredChild = DeadlineContext.open(earlierChild)) {
                assertThat(DeadlineContext.current()).contains(earlierChild);
            }
            assertThat(DeadlineContext.current()).contains(parent);
        }
        assertThat(DeadlineContext.current()).isEmpty();
    }

    /**
     * 验证作用域必须按嵌套顺序关闭且关闭失败不会破坏当前状态。
     */
    @Test
    void shouldRejectOutOfOrderScopeClose() {
        DeadlineContext.Scope outer = DeadlineContext.open(Deadline.after(Duration.ofSeconds(10), CLOCK));
        DeadlineContext.Scope inner = DeadlineContext.open(Deadline.after(Duration.ofSeconds(5), CLOCK));

        assertThatThrownBy(outer::close).isInstanceOf(IllegalStateException.class);
        inner.close();
        outer.close();
        assertThat(DeadlineContext.current()).isEmpty();
    }

    /**
     * 验证显式包装将 Deadline 传播到线程池并在任务后清理。
     */
    @Test
    void shouldPropagateAndCleanDeadlineAcrossThreads() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Runnable captured;
            Deadline deadline = Deadline.after(Duration.ofSeconds(10), CLOCK);
            try (DeadlineContext.Scope ignored = DeadlineContext.open(deadline)) {
                captured = DeadlineContext.wrap(() -> {
                    assertThat(DeadlineContext.current()).contains(deadline);
                });
            }

            CompletableFuture.runAsync(captured, executor).join();
            assertThat(CompletableFuture.supplyAsync(DeadlineContext::current, executor).join()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证到期 Deadline 抛出稳定公共异常。
     */
    @Test
    void shouldThrowWhenDeadlineExpires() {
        try (DeadlineContext.Scope ignored = DeadlineContext.open(Deadline.after(Duration.ZERO, CLOCK))) {
            assertThatThrownBy(DeadlineContext::check)
                    .isInstanceOf(DeadlineExceededException.class);
        }
    }
}
