package io.github.zhanghslq.muskit.lifecycle;

/**
 * 可排空组件的低基数状态快照。
 *
 * @param name 组件名称
 * @param state 当前状态
 * @param inflight 在途工作数量
 * @author zhs
 * @since 2026-08-20
 */
public record DrainSnapshot(String name, DrainState state, long inflight) {

    /**
     * 校验并创建排空状态快照。
     */
    public DrainSnapshot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("排空组件名称不能为空");
        }
        if (state == null) {
            throw new IllegalArgumentException("排空状态不能为空");
        }
        if (inflight < 0L) {
            throw new IllegalArgumentException("在途数量不能为负数");
        }
    }
}
