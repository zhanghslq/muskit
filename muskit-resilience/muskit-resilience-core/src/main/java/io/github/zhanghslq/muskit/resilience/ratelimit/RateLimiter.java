package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 限流判定 Provider SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RateLimiter {

    /**
     * 消耗一个令牌并返回限流判定。
     *
     * @param request 限流请求
     * @return 限流判定
     */
    RateLimitDecision tryAcquire(RateLimitRequest request);
}
