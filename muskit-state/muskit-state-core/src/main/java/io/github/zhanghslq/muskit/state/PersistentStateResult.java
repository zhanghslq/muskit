package io.github.zhanghslq.muskit.state;

import java.util.Objects;

/**
 * 持久化状态迁移结果及最终版本。
 *
 * @param <S> 状态类型
 * @author zhs
 * @since 2026-08-20
 */
public final class PersistentStateResult<S> {

    private final StateTransitionResult<S> transition;
    private final long version;
    private final int attempts;

    /**
     * 创建持久化迁移结果。
     *
     * @param transition 迁移判定
     * @param version 最终版本
     * @param attempts 乐观锁尝试次数
     */
    public PersistentStateResult(StateTransitionResult<S> transition, long version, int attempts) {
        this.transition = Objects.requireNonNull(transition, "状态迁移结果不能为空");
        this.version = version;
        this.attempts = attempts;
    }

    /**
     * 返回迁移判定。
     *
     * @return 迁移判定
     */
    public StateTransitionResult<S> transition() {
        return transition;
    }

    /**
     * 返回最终版本。
     *
     * @return 最终版本
     */
    public long version() {
        return version;
    }

    /**
     * 返回乐观锁尝试次数。
     *
     * @return 尝试次数
     */
    public int attempts() {
        return attempts;
    }
}
