package io.github.zhanghslq.muskit.state.spi;

/**
 * 判断一次状态迁移是否满足业务条件的守卫函数。
 *
 * @param <S> 状态类型
 * @param <E> 事件类型
 * @param <C> 业务上下文类型
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface StateGuard<S, E, C> {

    /**
     * 判断是否允许状态迁移。
     *
     * @param state 当前状态
     * @param event 业务事件
     * @param context 业务上下文
     * @return 是否允许迁移
     */
    boolean permits(S state, E event, C context);
}
