package io.github.zhanghslq.muskit.executor;

/**
 * 受管执行器使用的线程类型。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum ExecutorType {

    /** 固定数量的平台线程。 */
    PLATFORM,

    /** Java 21 虚拟线程。 */
    VIRTUAL
}
