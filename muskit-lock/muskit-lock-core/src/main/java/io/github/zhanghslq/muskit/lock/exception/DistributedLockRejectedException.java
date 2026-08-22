package io.github.zhanghslq.muskit.lock.exception;

/**
 * 表示在允许的等待时间内未获取到锁。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DistributedLockRejectedException extends RuntimeException {

    /**
     * 创建锁获取失败异常。
     *
     * @param lockName 低基数锁名称
     */
    public DistributedLockRejectedException(String lockName) {
        super("未能在等待时间内获取分布式锁，lock=" + lockName);
    }
}
