package io.github.zhanghslq.muskit.lock.spi;

import java.util.OptionalLong;

/**
 * 表示一次已经成功获取且可以幂等释放的锁。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface DistributedLockHandle extends AutoCloseable {

    /**
     * 返回当前锁获取顺序对应的 fencing token。
     *
     * @return 支持 fencing 时返回严格递增令牌，否则为空
     */
    default OptionalLong fencingToken() {
        return OptionalLong.empty();
    }

    /**
     * 释放当前锁，多次调用必须与一次调用具有相同效果。
     */
    @Override
    void close();
}
