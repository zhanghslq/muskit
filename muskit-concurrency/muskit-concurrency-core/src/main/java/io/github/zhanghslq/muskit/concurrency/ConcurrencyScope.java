package io.github.zhanghslq.muskit.concurrency;

/**
 * 并发控制资源的隔离范围。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum ConcurrencyScope {

    /** 同一策略的所有调用共享并发额度。 */
    GLOBAL,

    /** 同一策略下按业务键分别计算并发额度。 */
    KEY
}

