package io.github.zhanghslq.muskit.context.reactor;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * 在 Reactor Context 中显式写入和读取 Muskit 业务上下文的辅助工具。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitReactorContext {

    /**
     * 工具类不允许实例化。
     */
    private MuskitReactorContext() {
    }

    /**
     * 创建可传给 {@code contextWrite} 的上下文写入函数。
     *
     * @param muskitContext 业务上下文，空上下文会删除已有值
     * @return Reactor Context 写入函数
     */
    public static Function<Context, Context> with(MuskitContext muskitContext) {
        Objects.requireNonNull(muskitContext, "Muskit 业务上下文不能为空");
        return reactorContext -> muskitContext.isEmpty()
                ? reactorContext.delete(MuskitContextHolder.CONTEXT_KEY)
                : reactorContext.put(MuskitContextHolder.CONTEXT_KEY, muskitContext);
    }

    /**
     * 从 Reactor Context 读取 Muskit 业务上下文。
     *
     * @param contextView Reactor Context 只读视图
     * @return 业务上下文，不存在时返回空
     */
    public static Optional<MuskitContext> find(ContextView contextView) {
        Objects.requireNonNull(contextView, "Reactor Context 不能为空");
        return contextView.getOrEmpty(MuskitContextHolder.CONTEXT_KEY);
    }
}
