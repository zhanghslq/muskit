package io.github.zhanghslq.muskit.resilience.circuitbreaker;

/**
 * 引用了不存在的熔断策略时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class UnknownCircuitBreakerPolicyException extends IllegalArgumentException {

    /**
     * 使用低基数策略名称创建异常。
     *
     * @param policyName 策略名称
     */
    public UnknownCircuitBreakerPolicyException(String policyName) {
        super("未找到熔断策略: " + policyName);
    }
}
