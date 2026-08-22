package io.github.zhanghslq.muskit.test.ratelimit;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式令牌桶限流 Provider 的跨实例行为契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class DistributedRateLimiterContract extends RateLimiterContract {

    /**
     * 创建分布式限流 Provider 契约测试基类。
     */
    protected DistributedRateLimiterContract() {
    }

    /**
     * 返回代表第一个应用实例的限流 Provider。
     *
     * @return 第一个限流 Provider
     */
    protected abstract RateLimiter firstLimiter();

    /**
     * 返回代表第二个应用实例的限流 Provider。
     *
     * @return 第二个限流 Provider
     */
    protected abstract RateLimiter secondLimiter();

    /**
     * 让基础契约使用第一个应用实例验证单 Provider 行为。
     *
     * @return 第一个限流 Provider
     */
    @Override
    protected final RateLimiter limiter() {
        return firstLimiter();
    }

    /**
     * 验证多个应用实例共享同一个令牌桶容量。
     */
    @Test
    protected final void shouldShareBucketCapacityAcrossInstances() {
        RateLimitRequest request = request(RateLimitScope.GLOBAL, "", 1);

        assertTrue(firstLimiter().tryAcquire(request).allowed());
        assertFalse(secondLimiter().tryAcquire(request).allowed());
    }
}
