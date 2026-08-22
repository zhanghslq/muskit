package io.github.zhanghslq.muskit.state.machine;

import io.github.zhanghslq.muskit.state.spi.StateGuard;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 按当前状态和事件唯一定位迁移规则的不可变状态机定义。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 * @author zhs
 * @since 2026-08-20
 */
public final class StateMachineDefinition<S, E, C> {

    private final Map<S, Map<E, Transition<S, E, C>>> transitions;

    /**
     * 创建不可变状态机定义。
     *
     * @param transitions 状态迁移规则
     */
    private StateMachineDefinition(Map<S, Map<E, Transition<S, E, C>>> transitions) {
        LinkedHashMap<S, Map<E, Transition<S, E, C>>> copied = new LinkedHashMap<>();
        transitions.forEach((state, rules) -> copied.put(state, Map.copyOf(rules)));
        this.transitions = Map.copyOf(copied);
    }

    /**
     * 创建状态机定义构建器。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 业务上下文类型
     * @return 空构建器
     */
    public static <S, E, C> Builder<S, E, C> builder() {
        return new Builder<>();
    }

    /**
     * 查找当前状态和事件对应的迁移规则。
     *
     * @param state 当前状态
     * @param event 业务事件
     * @return 迁移规则，不存在时为空
     */
    Transition<S, E, C> find(S state, E event) {
        Map<E, Transition<S, E, C>> byEvent = transitions.get(state);
        return byEvent == null ? null : byEvent.get(event);
    }

    /**
     * 状态机定义构建器。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 业务上下文类型
     * @author zhs
     * @since 2026-08-20
     */
    public static final class Builder<S, E, C> {

        private final Map<S, Map<E, Transition<S, E, C>>> transitions = new LinkedHashMap<>();

        /**
         * 创建空状态机定义构建器。
         */
        public Builder() {
        }

        /**
         * 添加无条件迁移规则。
         *
         * @param from 来源状态
         * @param event 业务事件
         * @param to 目标状态
         * @return 当前构建器
         */
        public Builder<S, E, C> transition(S from, E event, S to) {
            return transition(from, event, to, (state, ignoredEvent, context) -> true);
        }

        /**
         * 添加带业务守卫的迁移规则。
         *
         * @param from 来源状态
         * @param event 业务事件
         * @param to 目标状态
         * @param guard 业务守卫
         * @return 当前构建器
         */
        public Builder<S, E, C> transition(
                S from,
                E event,
                S to,
                StateGuard<S, E, C> guard) {
            Objects.requireNonNull(from, "来源状态不能为空");
            Objects.requireNonNull(event, "状态事件不能为空");
            Objects.requireNonNull(to, "目标状态不能为空");
            Objects.requireNonNull(guard, "状态守卫不能为空");
            Map<E, Transition<S, E, C>> byEvent = transitions.computeIfAbsent(from, ignored -> new LinkedHashMap<>());
            if (byEvent.putIfAbsent(event, new Transition<>(to, guard)) != null) {
                throw new IllegalArgumentException("同一来源状态和事件只能定义一条迁移规则");
            }
            return this;
        }

        /**
         * 构建至少包含一条规则的不可变状态机定义。
         *
         * @return 状态机定义
         */
        public StateMachineDefinition<S, E, C> build() {
            if (transitions.isEmpty()) {
                throw new IllegalStateException("状态机至少需要一条迁移规则");
            }
            return new StateMachineDefinition<>(transitions);
        }
    }

    /**
     * 单条内部状态迁移规则。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 业务上下文类型
     * @author zhs
     * @since 2026-08-20
     */
    static final class Transition<S, E, C> {

        private final S target;
        private final StateGuard<S, E, C> guard;

        /**
         * 创建内部迁移规则。
         *
         * @param target 目标状态
         * @param guard 业务守卫
         */
        private Transition(S target, StateGuard<S, E, C> guard) {
            this.target = target;
            this.guard = guard;
        }

        /**
         * 返回目标状态。
         *
         * @return 目标状态
         */
        S target() {
            return target;
        }

        /**
         * 判断是否允许迁移。
         *
         * @param state 当前状态
         * @param event 业务事件
         * @param context 业务上下文
         * @return 是否允许
         */
        boolean permits(S state, E event, C context) {
            return guard.permits(state, event, context);
        }
    }
}
