package io.github.zhanghslq.muskit.context;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 当前线程的业务上下文持有器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitContextHolder {

    private static final ThreadLocal<MuskitContext> HOLDER = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private MuskitContextHolder() {
    }

    /**
     * 获取当前线程的业务上下文。
     *
     * @return 当前上下文，不存在时返回空
     */
    public static Optional<MuskitContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 获取当前线程的业务上下文，不存在时返回空上下文对象。
     *
     * @return 当前或空业务上下文
     */
    public static MuskitContext currentOrEmpty() {
        return current().orElseGet(MuskitContext::empty);
    }

    /**
     * 设置当前线程的业务上下文。
     *
     * @param context 业务上下文
     */
    public static void set(MuskitContext context) {
        if (context == null || context.isEmpty()) {
            clear();
            return;
        }
        HOLDER.set(context);
    }

    /**
     * 清理当前线程的业务上下文。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 打开一个上下文作用域，关闭作用域时恢复进入前的上下文。
     *
     * @param context 作用域内使用的业务上下文
     * @return 可关闭的上下文作用域
     */
    public static Scope open(MuskitContext context) {
        MuskitContext previous = HOLDER.get();
        set(context);
        return new Scope(previous);
    }

    /**
     * 可自动恢复的业务上下文作用域。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static final class Scope implements AutoCloseable {

        private final MuskitContext previous;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建上下文作用域。
         *
         * @param previous 进入作用域前的上下文
         */
        private Scope(MuskitContext previous) {
            this.previous = previous;
        }

        /**
         * 关闭作用域并恢复之前的上下文，重复关闭不会产生副作用。
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
