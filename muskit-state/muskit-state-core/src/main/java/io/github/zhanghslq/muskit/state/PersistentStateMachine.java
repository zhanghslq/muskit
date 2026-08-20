package io.github.zhanghslq.muskit.state;

import java.util.Objects;

/**
 * 基于仓储乐观锁安全提交状态迁移的执行器。
 *
 * @param <I> 实体标识类型
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 * @author zhs
 * @since 2026-08-20
 */
public final class PersistentStateMachine<I, S, E, C> {

    private final StateMachine<S, E, C> stateMachine;
    private final StateRepository<I, S> repository;
    private final int maxConflictRetries;

    /**
     * 创建持久化状态机。
     *
     * @param stateMachine 无副作用状态机
     * @param repository 状态仓储
     * @param maxConflictRetries 最大乐观锁重试次数
     */
    public PersistentStateMachine(
            StateMachine<S, E, C> stateMachine,
            StateRepository<I, S> repository,
            int maxConflictRetries) {
        this.stateMachine = Objects.requireNonNull(stateMachine, "状态机不能为空");
        this.repository = Objects.requireNonNull(repository, "状态仓储不能为空");
        if (maxConflictRetries < 0 || maxConflictRetries > 100) {
            throw new IllegalArgumentException("状态冲突重试次数必须在 0 到 100 之间");
        }
        this.maxConflictRetries = maxConflictRetries;
    }

    /**
     * 读取最新状态、执行迁移判定并通过乐观锁提交。
     *
     * @param id 实体标识
     * @param event 业务事件
     * @param context 业务上下文
     * @return 持久化迁移结果
     */
    public PersistentStateResult<S> fire(I id, E event, C context) {
        Objects.requireNonNull(id, "状态实体标识不能为空");
        for (int attempt = 1; attempt <= maxConflictRetries + 1; attempt++) {
            VersionedState<I, S> snapshot = repository.find(id).orElseThrow(UnknownStateEntityException::new);
            StateTransitionResult<S> transition = stateMachine.transition(snapshot.state(), event, context);
            if (!transition.applied()) {
                return new PersistentStateResult<>(transition, snapshot.version(), attempt);
            }
            // compareAndSet 必须由 Provider 原子实现；冲突后重新读取，绝不复用过期状态。
            if (repository.compareAndSet(id, snapshot.version(), transition.currentState())) {
                return new PersistentStateResult<>(transition, snapshot.version() + 1, attempt);
            }
        }
        throw new StateConflictException();
    }
}
