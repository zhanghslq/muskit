package io.github.zhanghslq.muskit.observation;

/**
 * Muskit 指标数据类型。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum MuskitMetricKind {

    /** 单调递增计数器。 */
    COUNTER,

    /** 调用耗时。 */
    TIMER,

    /** 可增减的当前值。 */
    GAUGE
}
