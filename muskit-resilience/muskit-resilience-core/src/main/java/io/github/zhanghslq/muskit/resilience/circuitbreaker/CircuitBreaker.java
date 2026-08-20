package io.github.zhanghslq.muskit.resilience.circuitbreaker;

/**
 * 熔断状态机 Provider SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface CircuitBreaker {

    /**
     * 尝试获取一次调用许可，熔断开启时明确拒绝。
     *
     * @param policy 熔断策略
     * @return 调用许可
     */
    CircuitBreakerPermit acquire(CircuitBreakerPolicy policy);

    /**
     * 返回指定策略当前状态。
     *
     * @param policy 熔断策略
     * @return 当前状态
     */
    CircuitBreakerState state(CircuitBreakerPolicy policy);
}
