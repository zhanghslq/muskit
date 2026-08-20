package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 本地限流桶数量达到保护上限时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class RateLimitBucketCapacityException extends RuntimeException {

    /**
     * 根据策略名称创建容量保护异常。
     *
     * @param policyName 限流策略名称
     */
    public RateLimitBucketCapacityException(String policyName) {
        super("本地限流桶数量达到上限，策略: " + policyName);
    }
}
