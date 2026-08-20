package io.github.zhanghslq.muskit.resilience.circuitbreaker;

/**
 * 根据稳定名称解析熔断策略的扩展点。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface CircuitBreakerPolicyResolver {

    /**
     * 解析指定熔断策略。
     *
     * @param policyName 策略名称
     * @return 熔断策略
     */
    CircuitBreakerPolicy resolve(String policyName);
}
