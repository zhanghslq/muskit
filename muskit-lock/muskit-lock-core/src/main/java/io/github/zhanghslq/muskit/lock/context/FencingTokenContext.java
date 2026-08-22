package io.github.zhanghslq.muskit.lock.context;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理当前线程分布式锁 fencing token 的嵌套作用域。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class FencingTokenContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    /**
     * 禁止实例化静态上下文工具类。
     */
    private FencingTokenContext() {
    }

    /**
     * 返回当前 fencing token。
     *
     * @return 当前 token，不存在时为空
     */
    public static OptionalLong current() {
        Long token = CURRENT.get();
        return token == null ? OptionalLong.empty() : OptionalLong.of(token);
    }

    /**
     * 打开 fencing token 作用域，关闭后恢复上一层值。
     *
     * @param token 正数 fencing token
     * @return 可关闭作用域
     */
    public static Scope open(long token) {
        if (token <= 0) {
            throw new IllegalArgumentException("fencing token 必须为正数");
        }
        Long previous = CURRENT.get();
        CURRENT.set(token);
        return new Scope(previous);
    }

    /**
     * 可幂等关闭的 fencing token 作用域。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static final class Scope implements AutoCloseable {

        private final Long previous;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建 fencing token 作用域。
         *
         * @param previous 上一层 token
         */
        private Scope(Long previous) {
            this.previous = previous;
        }

        /**
         * 恢复上一层 token，重复关闭保持幂等。
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
