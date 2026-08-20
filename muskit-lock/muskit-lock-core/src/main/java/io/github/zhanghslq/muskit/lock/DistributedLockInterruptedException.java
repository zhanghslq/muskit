package io.github.zhanghslq.muskit.lock;

/**
 * 表示等待分布式锁期间线程被中断。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DistributedLockInterruptedException extends RuntimeException {

    /**
     * 创建锁等待中断异常。
     *
     * @param lockName 低基数锁名称
     * @param cause 原始中断异常
     */
    public DistributedLockInterruptedException(String lockName, InterruptedException cause) {
        super("等待分布式锁时线程被中断，lock=" + lockName, cause);
    }
}
