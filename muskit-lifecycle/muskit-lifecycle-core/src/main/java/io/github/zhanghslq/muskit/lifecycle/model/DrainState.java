package io.github.zhanghslq.muskit.lifecycle.model;

/**
 * 可排空组件的生命周期状态。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum DrainState {

    /** 接受新工作。 */
    RUNNING,

    /** 拒绝新工作并等待在途工作结束。 */
    DRAINING,

    /** 已经没有在途工作。 */
    DRAINED
}
