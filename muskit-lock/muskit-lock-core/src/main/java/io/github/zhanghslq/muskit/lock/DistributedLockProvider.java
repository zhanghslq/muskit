package io.github.zhanghslq.muskit.lock;

import java.util.Optional;

/**
 * 定义锁的获取能力，基础设施实现不得在内部静默降低互斥语义。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface DistributedLockProvider {

    /**
     * 尝试在指定等待时间内获取锁。
     *
     * @param request 锁请求
     * @return 获取成功时返回锁句柄，否则返回空
     * @throws InterruptedException 等待锁期间线程被中断
     */
    Optional<DistributedLockHandle> tryAcquire(DistributedLockRequest request) throws InterruptedException;
}
