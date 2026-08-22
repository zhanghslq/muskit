package io.github.zhanghslq.muskit.lock.local;

import io.github.zhanghslq.muskit.lock.exception.FencingTokenUnavailableException;
import io.github.zhanghslq.muskit.lock.model.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提供仅当前 JVM 内有效的本地锁，用于业务显式允许的 Redis 故障降级。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class LocalDistributedLockProvider implements DistributedLockProvider {

    private final ConcurrentMap<LockIdentity, LockEntry> entries = new ConcurrentHashMap<>();

    /**
     * 创建本地锁提供器。
     */
    public LocalDistributedLockProvider() {
    }

    /**
     * 尝试获取当前 JVM 内的锁，固定租约在本地降级模式下不会提前释放锁。
     *
     * @param request 锁请求
     * @return 获取成功时返回本地锁句柄，否则返回空
     * @throws InterruptedException 等待锁期间线程被中断
     */
    @Override
    public Optional<DistributedLockHandle> tryAcquire(DistributedLockRequest request) throws InterruptedException {
        Objects.requireNonNull(request, "锁请求不能为空");
        if (request.fencing()) {
            throw new FencingTokenUnavailableException(request.name());
        }
        LockIdentity identity = new LockIdentity(request.name(), request.key());
        LockEntry entry = entries.compute(identity, (ignored, current) -> {
            LockEntry selected = current == null ? new LockEntry(request.fair()) : current;
            selected.retain();
            return selected;
        });

        boolean acquired = false;
        try {
            acquired = entry.tryAcquire(request.waitTime());
            if (!acquired) {
                releaseReference(identity, entry);
                return Optional.empty();
            }
            return Optional.of(new LocalLockHandle(identity, entry));
        } catch (InterruptedException exception) {
            releaseReference(identity, entry);
            throw exception;
        } catch (RuntimeException exception) {
            if (!acquired) {
                releaseReference(identity, entry);
            }
            throw exception;
        }
    }

    /**
     * 释放锁条目的一个引用，并在没有持有者或等待者时安全移除条目。
     *
     * @param identity 锁身份
     * @param expected 预期锁条目
     */
    private void releaseReference(LockIdentity identity, LockEntry expected) {
        entries.computeIfPresent(identity, (ignored, current) -> {
            if (current != expected) {
                return current;
            }
            return current.releaseReference() == 0 ? null : current;
        });
    }

    /**
     * 当前 JVM 内的锁身份，不在字符串表示中包含业务锁键。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class LockIdentity {

        private final String name;
        private final String key;

        /**
         * 创建本地锁身份。
         *
         * @param name 锁名称
         * @param key 业务锁键
         */
        private LockIdentity(String name, String key) {
            this.name = name;
            this.key = key;
        }

        /**
         * 比较锁名称和业务锁键是否完全一致。
         *
         * @param object 待比较对象
         * @return 是否为同一把本地锁
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof LockIdentity other)) {
                return false;
            }
            return name.equals(other.name) && key.equals(other.key);
        }

        /**
         * 计算锁名称和业务锁键的组合哈希值。
         *
         * @return 组合哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(name, key);
        }

        /**
         * 返回不包含业务锁键的安全描述。
         *
         * @return 安全描述
         */
        @Override
        public String toString() {
            return "LockIdentity[name=" + name + "]";
        }
    }

    /**
     * 保存一把本地锁及其持有者和等待者引用计数。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class LockEntry {

        private final Semaphore semaphore;
        private final AtomicInteger references = new AtomicInteger();

        /**
         * 创建本地锁条目。
         *
         * @param fair 是否使用公平等待顺序
         */
        private LockEntry(boolean fair) {
            semaphore = new Semaphore(1, fair);
        }

        /**
         * 增加一个持有者或等待者引用。
         */
        private void retain() {
            references.incrementAndGet();
        }

        /**
         * 尝试在指定时间内获取本地锁。
         *
         * @param waitTime 最长等待时间
         * @return 是否获取成功
         * @throws InterruptedException 等待期间线程被中断
         */
        private boolean tryAcquire(java.time.Duration waitTime) throws InterruptedException {
            if (waitTime.isZero()) {
                return semaphore.tryAcquire();
            }
            return semaphore.tryAcquire(waitTime.toNanos(), TimeUnit.NANOSECONDS);
        }

        /**
         * 释放本地锁许可。
         */
        private void releasePermit() {
            semaphore.release();
        }

        /**
         * 减少一个持有者或等待者引用。
         *
         * @return 剩余引用数
         */
        private int releaseReference() {
            return references.decrementAndGet();
        }
    }

    /**
     * 支持跨线程幂等释放的本地锁句柄。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private final class LocalLockHandle implements DistributedLockHandle {

        private final LockIdentity identity;
        private final LockEntry entry;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建本地锁句柄。
         *
         * @param identity 锁身份
         * @param entry 锁条目
         */
        private LocalLockHandle(LockIdentity identity, LockEntry entry) {
            this.identity = identity;
            this.entry = entry;
        }

        /**
         * 幂等释放本地锁和条目引用。
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            // Semaphore 不绑定获取线程，因此异步回调切换线程后仍可安全释放降级锁。
            entry.releasePermit();
            releaseReference(identity, entry);
        }
    }
}
