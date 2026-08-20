package io.github.zhanghslq.muskit.lock;

/**
 * 表示一次已经成功获取且可以幂等释放的锁。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface DistributedLockHandle extends AutoCloseable {

    /**
     * 释放当前锁，多次调用必须与一次调用具有相同效果。
     */
    @Override
    void close();
}
