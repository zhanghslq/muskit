package io.github.zhanghslq.muskit.concurrency.local;

import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 JDK Semaphore 的进程内并发额度提供器，兼容平台线程和虚拟线程。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class LocalConcurrencyLimiter implements ConcurrencyLimiter {

    private final ConcurrentMap<SlotKey, Slot> slots = new ConcurrentHashMap<>();

    /**
     * 创建进程内并发额度提供器。
     */
    public LocalConcurrencyLimiter() {
    }

    /**
     * 尝试获取本地并发额度。
     *
     * @param request 并发额度请求
     * @return 获取成功时返回并发额度，否则返回空
     * @throws InterruptedException 等待期间线程被中断
     */
    @Override
    public Optional<ConcurrencyPermit> tryAcquire(ConcurrencyRequest request) throws InterruptedException {
        SlotKey slotKey = SlotKey.from(request);
        // 引用数同时覆盖等待者和持有者，防止业务键槽位在等待期间被其他线程清理。
        Slot slot = slots.compute(slotKey, (key, current) -> retain(current, request.policy()));
        boolean acquired;
        try {
            acquired = slot.tryAcquire(request.policy().maxWait());
        } catch (InterruptedException | RuntimeException exception) {
            releaseReference(slotKey, slot);
            throw exception;
        }
        if (!acquired) {
            releaseReference(slotKey, slot);
            return Optional.empty();
        }
        return Optional.of(new LocalPermit(this, slotKey, slot));
    }

    /**
     * 返回当前仍被调用引用的并发槽位数量，主要用于诊断和测试。
     *
     * @return 活跃槽位数量
     */
    public int activeSlotCount() {
        return slots.size();
    }

    /**
     * 获取或创建槽位并增加引用计数。
     *
     * @param current 当前槽位
     * @param policy 并发策略
     * @return 已增加引用计数的槽位
     */
    private Slot retain(Slot current, ConcurrencyPolicy policy) {
        Slot resolved = current == null
                ? new Slot(policy.maxConcurrency(), policy.fair())
                : current;
        resolved.retain();
        return resolved;
    }

    /**
     * 减少槽位引用计数，并在没有调用使用时将其安全移除。
     *
     * @param slotKey 槽位键
     * @param slot 待释放引用的槽位
     */
    private void releaseReference(SlotKey slotKey, Slot slot) {
        slots.computeIfPresent(slotKey, (key, current) -> {
            if (current != slot) {
                return current;
            }
            return current.releaseReference() == 0 ? null : current;
        });
    }

    /**
     * 释放已经获取的额度及其槽位引用。
     *
     * @param slotKey 槽位键
     * @param slot 槽位
     */
    private void releasePermit(SlotKey slotKey, Slot slot) {
        slot.releasePermit();
        releaseReference(slotKey, slot);
    }

    /**
     * 单个本地并发槽位的标识。
     *
     * @param policyName 策略名称
     * @param resourceKey 业务隔离键
     * @param maxConcurrency 最大并发数
     * @param fair 是否公平
     * @author zhs
     * @since 2026-08-20
     */
    private record SlotKey(String policyName, String resourceKey, int maxConcurrency, boolean fair) {

        /**
         * 根据并发请求创建槽位标识。
         *
         * @param request 并发额度请求
         * @return 槽位标识
         */
        private static SlotKey from(ConcurrencyRequest request) {
            ConcurrencyPolicy policy = request.policy();
            return new SlotKey(policy.name(), request.effectiveKey(), policy.maxConcurrency(), policy.fair());
        }
    }

    /**
     * 保存信号量及其活跃调用引用数。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class Slot {

        private final Semaphore semaphore;
        private int references;

        /**
         * 创建本地并发槽位。
         *
         * @param maxConcurrency 最大并发数
         * @param fair 是否公平
         */
        private Slot(int maxConcurrency, boolean fair) {
            this.semaphore = new Semaphore(maxConcurrency, fair);
        }

        /**
         * 增加活跃调用引用数。
         */
        private void retain() {
            references++;
        }

        /**
         * 减少活跃调用引用数。
         *
         * @return 剩余引用数
         */
        private int releaseReference() {
            return --references;
        }

        /**
         * 在限定时间内尝试获取信号量许可。
         *
         * @param maxWait 最大等待时间
         * @return 是否获取成功
         * @throws InterruptedException 等待期间线程被中断
         */
        private boolean tryAcquire(Duration maxWait) throws InterruptedException {
            if (maxWait.isZero()) {
                return semaphore.tryAcquire();
            }
            return semaphore.tryAcquire(toNanosSaturated(maxWait), TimeUnit.NANOSECONDS);
        }

        /**
         * 释放信号量许可。
         */
        private void releasePermit() {
            semaphore.release();
        }

        /**
         * 将 Duration 转换为纳秒，溢出时使用 Long 最大值。
         *
         * @param duration 待转换时间
         * @return 纳秒数
         */
        private static long toNanosSaturated(Duration duration) {
            try {
                return duration.toNanos();
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
    }

    /**
     * 本地并发额度句柄，使用原子状态保证重复关闭安全。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class LocalPermit implements ConcurrencyPermit {

        private final LocalConcurrencyLimiter owner;
        private final SlotKey slotKey;
        private final Slot slot;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建本地并发额度句柄。
         *
         * @param owner 所属额度提供器
         * @param slotKey 槽位键
         * @param slot 槽位
         */
        private LocalPermit(LocalConcurrencyLimiter owner, SlotKey slotKey, Slot slot) {
            this.owner = owner;
            this.slotKey = slotKey;
            this.slot = slot;
        }

        /**
         * 释放并发额度，重复调用不会重复释放信号量。
         */
        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.releasePermit(slotKey, slot);
            }
        }
    }
}
