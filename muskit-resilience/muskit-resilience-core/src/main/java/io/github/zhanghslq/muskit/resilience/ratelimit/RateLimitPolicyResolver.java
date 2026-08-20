package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 根据名称解析限流策略的 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RateLimitPolicyResolver {

    /**
     * 解析指定限流策略。
     *
     * @param policyName 策略名称
     * @return 限流策略
     */
    RateLimitPolicy resolve(String policyName);
}
