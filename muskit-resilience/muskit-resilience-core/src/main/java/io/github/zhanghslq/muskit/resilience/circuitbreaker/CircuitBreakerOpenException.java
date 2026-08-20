package io.github.zhanghslq.muskit.resilience.circuitbreaker;

/**
 * 熔断器处于拒绝调用状态时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class CircuitBreakerOpenException extends RuntimeException {

    /**
     * 使用低基数策略名称创建异常。
     *
     * @param policyName 策略名称
     */
    public CircuitBreakerOpenException(String policyName) {
        super("熔断器拒绝调用，策略: " + policyName);
    }
}
