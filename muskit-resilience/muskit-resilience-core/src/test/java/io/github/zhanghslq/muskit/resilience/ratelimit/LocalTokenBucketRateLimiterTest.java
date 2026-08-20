package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地令牌桶限流器单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class LocalTokenBucketRateLimiterTest {

    /**
     * 验证令牌桶容量、连续补充和建议等待时间。
     */
    @Test
    void shouldRefillTokensContinuously() {
        AtomicLong ticker = new AtomicLong();
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter(
                10, Duration.ofMinutes(1), ticker::get);
        RateLimitRequest request = request("tenant-a");

        assertThat(limiter.tryAcquire(request).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request).retryAfter()).isEqualTo(Duration.ofSeconds(1));

        ticker.addAndGet(Duration.ofMillis(500).toNanos());
        assertThat(limiter.tryAcquire(request).retryAfter()).isEqualTo(Duration.ofMillis(500));
        ticker.addAndGet(Duration.ofMillis(500).toNanos());
        assertThat(limiter.tryAcquire(request).allowed()).isTrue();
    }

    /**
     * 验证 KEY 范围为不同业务键维护独立令牌桶。
     */
    @Test
    void shouldIsolateBucketsByBusinessKey() {
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter();

        assertThat(limiter.tryAcquire(request("tenant-a")).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request("tenant-a")).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request("tenant-a")).allowed()).isFalse();
        assertThat(limiter.tryAcquire(request("tenant-b")).allowed()).isTrue();
    }

    /**
     * 验证桶数量硬上限以及空闲桶清理后可重新接纳业务键。
     */
    @Test
    void shouldBoundAndCleanUpKeyBuckets() {
        AtomicLong ticker = new AtomicLong();
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter(
                1, Duration.ofNanos(10), ticker::get);
        limiter.tryAcquire(request("tenant-a"));

        assertThatThrownBy(() -> limiter.tryAcquire(request("tenant-b")))
                .isInstanceOf(RateLimitBucketCapacityException.class)
                .hasMessageContaining("api");

        ticker.set(10);
        assertThat(limiter.tryAcquire(request("tenant-b")).allowed()).isTrue();
    }

    /**
     * 创建每秒补充一个、容量为两个令牌的测试请求。
     *
     * @param key 业务隔离键
     * @return 限流请求
     */
    private RateLimitRequest request(String key) {
        return new RateLimitRequest(
                new RateLimitPolicy("api", 2, 1, Duration.ofSeconds(1), RateLimitScope.KEY),
                key);
    }
}
