package io.github.zhanghslq.muskit.resilience.singleflight;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SingleFlight 并发合并单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class SingleFlightTest {

    /**
     * 验证同键并发调用共享一次真实异步执行。
     */
    @Test
    void shouldShareOneExecutionForSameKey() {
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        CompletableFuture<String> source = new CompletableFuture<>();
        AtomicInteger executions = new AtomicInteger();

        CompletableFuture<String> first = singleFlight.execute("order", () -> {
            executions.incrementAndGet();
            return source;
        }).toCompletableFuture();
        CompletableFuture<String> second = singleFlight.execute("order", () -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture("unexpected");
        }).toCompletableFuture();

        assertThat(executions).hasValue(1);
        assertThat(singleFlight.inFlightCount()).isOne();
        source.complete("result");
        assertThat(first).isCompletedWithValue("result");
        assertThat(second).isCompletedWithValue("result");
        assertThat(singleFlight.inFlightCount()).isZero();
    }

    /**
     * 验证单个调用方取消自己的视图不会取消共享业务执行。
     */
    @Test
    void shouldIsolateCallerCancellation() {
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        CompletableFuture<String> source = new CompletableFuture<>();
        CompletableFuture<String> cancelled = singleFlight.execute("order", () -> source).toCompletableFuture();
        CompletableFuture<String> waiting = singleFlight.execute("order", () -> source).toCompletableFuture();

        cancelled.cancel(false);
        source.complete("result");

        assertThat(cancelled).isCancelled();
        assertThat(waiting).isCompletedWithValue("result");
    }

    /**
     * 验证失败结果会清理占位，使后续调用可以重新执行。
     */
    @Test
    void shouldRetryAfterFailure() {
        SingleFlight<String, String> singleFlight = new SingleFlight<>();

        assertThatThrownBy(() -> singleFlight.executeSync("order", () -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(singleFlight.inFlightCount()).isZero();
        assertThat(singleFlight.execute("order", () -> CompletableFuture.completedFuture("retried")))
                .succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo("retried");
    }
}
