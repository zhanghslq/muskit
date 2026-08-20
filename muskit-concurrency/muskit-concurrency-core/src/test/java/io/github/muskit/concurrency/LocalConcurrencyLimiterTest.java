package io.github.muskit.concurrency;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muskit.test.ConcurrentTestSupport;
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
     */
    @Test
    void shouldLimitVirtualThreadConcurrency() {
        LocalConcurrencyLimiter limiter = new LocalConcurrencyLimiter();
        ConcurrencyPolicy policy = new ConcurrencyPolicy(
                "virtual-tasks", 4, Duration.ofSeconds(2), ConcurrencyScope.GLOBAL, false);
        ConcurrencyRequest request = new ConcurrencyRequest(policy, "");
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        ConcurrentTestSupport.runConcurrently(40, Duration.ofSeconds(5), index -> {
            try (ConcurrencyPermit ignored = limiter.tryAcquire(request).orElseThrow()) {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                Thread.sleep(Duration.ofMillis(10));
                active.decrementAndGet();
            }
        });

        assertThat(maximum.get()).isLessThanOrEqualTo(4);
        assertThat(limiter.activeSlotCount()).isZero();
    }
}
