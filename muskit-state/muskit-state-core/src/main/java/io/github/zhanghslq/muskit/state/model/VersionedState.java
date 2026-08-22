package io.github.zhanghslq.muskit.state.model;

import java.util.Objects;

/**
 * 带乐观锁版本号的持久化状态快照。
 *
 * @param <I> 实体标识类型
 * @param <S> 状态类型
 * @author zhs
 * @since 2026-08-20
 */
public final class VersionedState<I, S> {

    private final I id;
    private final S state;
    private final long version;

    /**
     * 创建版本化状态快照。
     *
     * @param id 实体标识
     * @param state 当前状态
     * @param version 非负版本号
     */
    public VersionedState(I id, S state, long version) {
        this.id = Objects.requireNonNull(id, "状态实体标识不能为空");
        this.state = Objects.requireNonNull(state, "持久化状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("状态版本号不能为负数");
        }
        this.version = version;
    }

    /**
     * 返回实体标识。
     *
     * @return 实体标识
     */
    public I id() {
        return id;
    }

    /**
     * 返回当前状态。
     *
     * @return 当前状态
     */
    public S state() {
        return state;
    }

    /**
     * 返回当前版本号。
     *
     * @return 版本号
     */
    public long version() {
        return version;
    }
}
