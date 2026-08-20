package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 引用了未配置限流策略时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class UnknownRateLimitPolicyException extends RuntimeException {

    /**
     * 根据策略名称创建异常。
     *
     * @param policyName 未知策略名称
     */
    public UnknownRateLimitPolicyException(String policyName) {
        super("未找到限流策略: " + policyName);
    }
}
