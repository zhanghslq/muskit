package io.github.zhanghslq.muskit.lifecycle.service;

import io.github.zhanghslq.muskit.lifecycle.exception.DrainInterruptedException;
import io.github.zhanghslq.muskit.lifecycle.model.DrainSnapshot;
import io.github.zhanghslq.muskit.lifecycle.model.DrainState;
import io.github.zhanghslq.muskit.lifecycle.spi.Drainable;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用锁保护状态转换和在途计数的可恢复排空控制器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainController implements Drainable {

    private final String name;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition drained = lock.newCondition();
    private DrainState state = DrainState.RUNNING;
    private long inflight;

    /**
     * 创建接受新工作的排空控制器。
     *
     * @param name 低基数组件名称
     */
    public DrainController(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("排空组件名称不能为空");
        }
        this.name = name;
    }

    /**
     * 返回组件名称。
     *
     * @return 组件名称
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * 在运行状态下登记一项新工作。
     *
     * @return 获得的在途许可；排空期间返回空
     */
    public Optional<DrainPermit> tryEnter() {
        lock.lock();
        try {
            if (state != DrainState.RUNNING) {
                return Optional.empty();
            }
            inflight++;
            return Optional.of(new DrainPermit(this::release));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 拒绝后续新工作；没有在途工作时立即进入已排空状态。
     */
    @Override
    public void beginDrain() {
        lock.lock();
        try {
            if (state == DrainState.RUNNING) {
                state = inflight == 0L ? DrainState.DRAINED : DrainState.DRAINING;
            }
            if (state == DrainState.DRAINED) {
                drained.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在没有在途工作时恢复接受新工作。
     */
    public void startAccepting() {
        lock.lock();
        try {
            if (inflight != 0L) {
                throw new IllegalStateException("存在在途工作时不能恢复接流");
            }
            state = DrainState.RUNNING;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 等待在途工作归零，并在中断时恢复线程中断标记。
     *
     * @param timeout 最大等待时间
     * @return 是否在期限内完成排空
     */
    @Override
    public boolean awaitDrained(Duration timeout) {
        requireNonNegative(timeout);
        beginDrain();
        long remaining = saturatedNanos(timeout);
        lock.lock();
        try {
            while (state != DrainState.DRAINED) {
                if (remaining <= 0L) {
                    return false;
                }
                try {
                    remaining = drained.awaitNanos(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DrainInterruptedException(name, exception);
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回当前状态的一致性快照。
     *
     * @return 排空状态快照
     */
    public DrainSnapshot snapshot() {
        lock.lock();
        try {
            return new DrainSnapshot(name, state, inflight);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 释放一项在途工作，并在排空期间归零时唤醒全部等待者。
     */
    private void release() {
        lock.lock();
        try {
            if (inflight <= 0L) {
                throw new IllegalStateException("排空在途计数已经为零");
            }
            inflight--;
            if (inflight == 0L && state == DrainState.DRAINING) {
                state = DrainState.DRAINED;
                drained.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验非负等待时间。
     *
     * @param timeout 等待时间
     */
    private void requireNonNegative(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("排空等待时间不能为负数");
        }
    }

    /**
     * 将时间转换为饱和纳秒值。
     *
     * @param timeout 等待时间
     * @return 纳秒值
     */
    private long saturatedNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return TimeUnit.DAYS.toNanos(365L * 100L);
        }
    }
}
