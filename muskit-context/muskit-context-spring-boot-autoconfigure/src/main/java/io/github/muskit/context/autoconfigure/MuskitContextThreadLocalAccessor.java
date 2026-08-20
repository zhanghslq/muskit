package io.github.muskit.context.autoconfigure;

import io.github.muskit.context.MuskitContext;
import io.github.muskit.context.MuskitContextHolder;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * 将 Muskit 业务上下文接入 Micrometer Context Propagation。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitContextThreadLocalAccessor implements ThreadLocalAccessor<MuskitContext> {

    /** Muskit 业务上下文注册键。 */
    public static final String KEY = "muskit.context";

    /**
     * 创建 Muskit 线程上下文访问器。
     */
    public MuskitContextThreadLocalAccessor() {
    }

    /**
     * 返回上下文注册键。
     *
     * @return 上下文注册键
     */
    @Override
    public Object key() {
        return KEY;
    }

    /**
     * 读取当前线程的 Muskit 业务上下文。
     *
     * @return 当前上下文，不存在时返回 null
     */
    @Override
    public MuskitContext getValue() {
        return MuskitContextHolder.current().orElse(null);
    }

    /**
     * 将指定 Muskit 业务上下文设置到当前线程。
     *
     * @param value 业务上下文
     */
    @Override
    public void setValue(MuskitContext value) {
        MuskitContextHolder.set(value);
    }

    /**
     * 清理当前线程的 Muskit 业务上下文。
     */
    @Override
    public void setValue() {
        MuskitContextHolder.clear();
    }
}
