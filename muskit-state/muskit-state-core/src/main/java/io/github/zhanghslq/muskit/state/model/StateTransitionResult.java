package io.github.zhanghslq.muskit.state.model;

import java.util.Objects;

/**
 * 一次不可变状态迁移判定结果。
 *
 * @param <S> 状态类型
 * @author zhs
 * @since 2026-08-20
 */
public final class StateTransitionResult<S> {

    private final StateTransitionStatus status;
    private final S previousState;
    private final S currentState;

    /**
     * 创建状态迁移结果。
     *
     * @param status 迁移状态
     * @param previousState 迁移前状态
     * @param currentState 迁移后或保持的状态
     */
    public StateTransitionResult(StateTransitionStatus status, S previousState, S currentState) {
        this.status = Objects.requireNonNull(status, "迁移结果状态不能为空");
        this.previousState = Objects.requireNonNull(previousState, "迁移前状态不能为空");
        this.currentState = Objects.requireNonNull(currentState, "迁移后状态不能为空");
    }

    /**
     * 返回迁移结果状态。
     *
     * @return 迁移结果状态
     */
    public StateTransitionStatus status() {
        return status;
    }

    /**
     * 返回迁移前状态。
     *
     * @return 迁移前状态
     */
    public S previousState() {
        return previousState;
    }

    /**
     * 返回迁移后或保持的状态。
     *
     * @return 当前状态
     */
    public S currentState() {
        return currentState;
    }

    /**
     * 返回是否成功应用迁移。
     *
     * @return 是否成功迁移
     */
    public boolean applied() {
        return status == StateTransitionStatus.APPLIED;
    }
}
