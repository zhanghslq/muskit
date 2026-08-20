package io.github.zhanghslq.muskit.concurrency;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LocalConcurrencyLimiter 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class LocalConcurrencyLimiterTest {

    /**
     * 验证全局并发额度耗尽后拒绝新请求，并在关闭后清理槽位。
     *
     * @throws InterruptedException 测试线程被中断
     */
    @Test
    void shouldRejectAndCleanupGlobalPermit() throws InterruptedException {
        LocalConcurrencyLimiter limiter = new LocalConcurrencyLimiter();
        ConcurrencyPolicy policy = new ConcurrencyPolicy(
                "payment-api", 1, Duration.ZERO, ConcurrencyScope.GLOBAL, false);
        ConcurrencyRequest request = new ConcurrencyRequest(policy, "ignored");

        ConcurrencyPermit first = limiter.tryAcquire(request).orElseThrow();
        Optional<ConcurrencyPermit> second = limiter.tryAcquire(request);

        assertThat(second).isEmpty();
        assertThat(limiter.activeSlotCount()).isEqualTo(1);

        first.close();
        first.close();
        assertThat(limiter.activeSlotCount()).isZero();
    }

    /**
     * 验证按业务键隔离时不同业务键拥有独立额度。
     *
     * @throws InterruptedException 测试线程被中断
     */
    @Test
    void shouldIsolatePermitByBusinessKey() throws InterruptedException {
        LocalConcurrencyLimiter limiter = new LocalConcurrencyLimiter();
        ConcurrencyPolicy policy = new ConcurrencyPolicy(
                "tenant-export", 1, Duration.ZERO, ConcurrencyScope.KEY, false);

        try (ConcurrencyPermit tenantA = limiter.tryAcquire(new ConcurrencyRequest(policy, "A")).orElseThrow();
                ConcurrencyPermit tenantB = limiter.tryAcquire(new ConcurrencyRequest(policy, "B")).orElseThrow()) {
            assertThat(limiter.tryAcquire(new ConcurrencyRequest(policy, "A"))).isEmpty();
            assertThat(limiter.activeSlotCount()).isEqualTo(2);
        }

        assertThat(limiter.activeSlotCount()).isZero();
    }

    /**
     * 验证大量虚拟线程同时执行时实际并发数不会超过策略上限。
     *
     * @throws Exception 并发任务执行失败或等待超时
     */
    @Test
    void shouldLimitVirtualThreadConcurrency() throws Exception {
        LocalConcurrencyLimiter limiter = new LocalConcurrencyLimiter();
        ConcurrencyPolicy policy = new ConcurrencyPolicy(
                "virtual-tasks", 4, Duration.ofSeconds(2), ConcurrencyScope.GLOBAL, false);
        ConcurrencyRequest request = new ConcurrencyRequest(policy, "");
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> tasks = IntStream.range(0, 40)
                    .mapToObj(index -> CompletableFuture.runAsync(() -> {
                        try (ConcurrencyPermit ignored = limiter.tryAcquire(request).orElseThrow()) {
                            int current = active.incrementAndGet();
                            try {
                                maximum.accumulateAndGet(current, Math::max);
                                Thread.sleep(Duration.ofMillis(10));
                            } finally {
                                active.decrementAndGet();
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException(exception);
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .get(5, TimeUnit.SECONDS);
        }

        assertThat(maximum.get()).isLessThanOrEqualTo(4);
        assertThat(maximum.get()).isGreaterThan(1);
        assertThat(limiter.activeSlotCount()).isZero();
    }
}
