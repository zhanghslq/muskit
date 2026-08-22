package io.github.zhanghslq.muskit.executor.model;

/**
 * 受管任务被拒绝的稳定原因。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum TaskRejectionReason {

    /** 执行器正在排空。 */
    DRAINING,

    /** 执行器运行与等待容量均已耗尽。 */
    CAPACITY,

    /** 执行器已经关闭。 */
    CLOSED
}
