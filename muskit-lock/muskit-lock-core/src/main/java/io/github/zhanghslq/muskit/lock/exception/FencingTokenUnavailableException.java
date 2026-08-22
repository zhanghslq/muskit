package io.github.zhanghslq.muskit.lock.exception;

/**
 * 表示当前锁实现无法提供分布式 fencing token。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class FencingTokenUnavailableException extends RuntimeException {

    /**
     * 创建 fencing token 不可用异常。
     *
     * @param lockName 低基数锁名称
     */
    public FencingTokenUnavailableException(String lockName) {
        super("分布式锁 fencing token 不可用，lock=" + lockName);
    }

    /**
     * 创建带安全异常类型说明的 fencing token 不可用异常。
     *
     * @param lockName 低基数锁名称
     * @param cause 原异常
     */
    public FencingTokenUnavailableException(String lockName, Throwable cause) {
        super("分布式锁 fencing token 不可用，lock=" + lockName,
                new IllegalStateException("后端异常类型：" + cause.getClass().getName()));
    }
}
