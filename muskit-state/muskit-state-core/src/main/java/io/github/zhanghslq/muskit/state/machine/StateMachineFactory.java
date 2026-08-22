package io.github.zhanghslq.muskit.state.machine;

import io.github.zhanghslq.muskit.state.spi.StateRepository;

/**
 * 使用统一乐观锁重试配置创建状态机执行器的工厂。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class StateMachineFactory {

    private final int maxConflictRetries;

    /**
     * 创建状态机工厂。
     *
     * @param maxConflictRetries 默认最大乐观锁重试次数
     */
    public StateMachineFactory(int maxConflictRetries) {
        if (maxConflictRetries < 0 || maxConflictRetries > 100) {
            throw new IllegalArgumentException("状态冲突重试次数必须在 0 到 100 之间");
        }
        this.maxConflictRetries = maxConflictRetries;
    }

    /**
     * 创建内存状态迁移判定器。
     *
     * @param definition 状态机定义
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 业务上下文类型
     * @return 状态机
     */
    public <S, E, C> StateMachine<S, E, C> create(StateMachineDefinition<S, E, C> definition) {
        return new StateMachine<>(definition);
    }

    /**
     * 创建持久化状态机。
     *
     * @param definition 状态机定义
     * @param repository 状态仓储
     * @param <I> 实体标识类型
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 业务上下文类型
     * @return 持久化状态机
     */
    public <I, S, E, C> PersistentStateMachine<I, S, E, C> createPersistent(
            StateMachineDefinition<S, E, C> definition,
            StateRepository<I, S> repository) {
        return new PersistentStateMachine<>(create(definition), repository, maxConflictRetries);
    }
}
