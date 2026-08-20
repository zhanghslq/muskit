package io.github.zhanghslq.muskit.state;

import java.util.Objects;

/**
 * 执行无副作用状态迁移判定的状态机。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 * @author zhs
 * @since 2026-08-20
 */
public final class StateMachine<S, E, C> {

    private final StateMachineDefinition<S, E, C> definition;

    /**
     * 创建状态机。
     *
     * @param definition 不可变状态机定义
     */
    public StateMachine(StateMachineDefinition<S, E, C> definition) {
        this.definition = Objects.requireNonNull(definition, "状态机定义不能为空");
    }

    /**
     * 判定当前状态是否可以消费事件并迁移。
     *
     * @param currentState 当前状态
     * @param event 业务事件
     * @param context 业务上下文
     * @return 迁移结果
     */
    public StateTransitionResult<S> transition(S currentState, E event, C context) {
        Objects.requireNonNull(currentState, "当前状态不能为空");
        Objects.requireNonNull(event, "状态事件不能为空");
        StateMachineDefinition.Transition<S, E, C> transition = definition.find(currentState, event);
        if (transition == null) {
            return new StateTransitionResult<>(StateTransitionStatus.NO_TRANSITION, currentState, currentState);
        }
        if (!transition.permits(currentState, event, context)) {
            return new StateTransitionResult<>(StateTransitionStatus.GUARD_REJECTED, currentState, currentState);
        }
        return new StateTransitionResult<>(StateTransitionStatus.APPLIED, currentState, transition.target());
    }
}
