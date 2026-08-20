package io.github.zhanghslq.muskit.resilience.circuitbreaker;

/**
 * 与底层实现无关的熔断状态。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum CircuitBreakerState {

    /** 正常放行并统计调用。 */
    CLOSED,

    /** 拒绝受保护调用。 */
    OPEN,

    /** 有界放行探测调用。 */
    HALF_OPEN,

    /** 只统计指标但不熔断。 */
    METRICS_ONLY,

    /** 熔断能力已禁用。 */
    DISABLED,

    /** 被运维操作强制开启。 */
    FORCED_OPEN
}
