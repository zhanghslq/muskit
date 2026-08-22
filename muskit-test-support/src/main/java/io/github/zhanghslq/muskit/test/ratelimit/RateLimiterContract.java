package io.github.zhanghslq.muskit.test.ratelimit;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitDecision;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicy;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有令牌桶限流 Provider 都应通过的基础行为契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class RateLimiterContract {

    /**
     * 创建令牌桶限流 Provider 契约测试基类。
     */
    protected RateLimiterContract() {
    }

    /**
     * 返回待验证的限流 Provider。
     *
     * @return 限流 Provider
     */
    protected abstract RateLimiter limiter();

    /**
     * 验证令牌桶容量耗尽后返回带等待建议的拒绝判定。
     */
    @Test
    protected final void shouldRejectAfterCapacityIsConsumed() {
        RateLimitRequest request = request(RateLimitScope.GLOBAL, "", 2);

        assertTrue(limiter().tryAcquire(request).allowed());
        assertTrue(limiter().tryAcquire(request).allowed());
        RateLimitDecision rejected = limiter().tryAcquire(request);

        assertFalse(rejected.allowed());
        assertTrue(rejected.retryAfter().isPositive());
    }

    /**
     * 验证按键策略为不同业务键维护独立令牌桶。
     */
    @Test
    protected final void shouldIsolateBucketsByBusinessKey() {
        RateLimitRequest first = request(RateLimitScope.KEY, UUID.randomUUID().toString(), 1);
        RateLimitRequest second = new RateLimitRequest(first.policy(), UUID.randomUUID().toString());

        assertTrue(limiter().tryAcquire(first).allowed());
        assertFalse(limiter().tryAcquire(first).allowed());
        assertTrue(limiter().tryAcquire(second).allowed());
    }

    /**
     * 创建使用长补充周期的测试请求，避免测试过程中自然补充令牌。
     *
     * @param scope 限流隔离范围
     * @param key 业务键
     * @param capacity 令牌桶容量
     * @return 限流请求
     */
    protected final RateLimitRequest request(
            RateLimitScope scope,
            String key,
            int capacity) {
        return new RateLimitRequest(
                new RateLimitPolicy(
                        "provider-contract-" + UUID.randomUUID(),
                        capacity,
                        1,
                        Duration.ofHours(1),
                        scope),
                key);
    }
}
