package io.github.zhanghslq.muskit.idempotency;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定期续期处理中幂等所有权并在关闭时报告续期失败的句柄。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyLease implements AutoCloseable {

    private final ScheduledFuture<?> future;
    private final AtomicReference<RuntimeException> failure;
    private final AtomicBoolean active;

    /**
     * 创建幂等续期句柄。
     *
     * @param future 定时续期任务
     * @param failure 续期失败引用
     * @param active 是否继续续期
     */
    private IdempotencyLease(
            ScheduledFuture<?> future,
            AtomicReference<RuntimeException> failure,
            AtomicBoolean active) {
        this.future = future;
        this.failure = failure;
        this.active = active;
    }

    /**
     * 按处理超时时间三分之一的周期启动续期。
     *
     * @param store 幂等状态存储
     * @param claim 幂等所有权声明
     * @param processingTimeout 处理超时时间
     * @param scheduler 定时执行器
     * @return 可关闭续期句柄
     */
    public static IdempotencyLease start(
            IdempotencyStore store,
            IdempotencyClaim claim,
            Duration processingTimeout,
            ScheduledExecutorService scheduler) {
        Objects.requireNonNull(store, "幂等状态存储不能为空");
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        Objects.requireNonNull(processingTimeout, "处理超时时间不能为空");
        Objects.requireNonNull(scheduler, "幂等续期执行器不能为空");
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("处理超时时间必须为正数");
        }
        long intervalMillis = Math.max(1L, processingTimeout.toMillis() / 3L);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicBoolean active = new AtomicBoolean(true);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            synchronized (active) {
                if (!active.get() || failure.get() != null) {
                    return;
                }
                try {
                    store.renew(claim, processingTimeout);
                } catch (RuntimeException exception) {
                    // 只保存第一次失败，业务完成线程会以稳定顺序观察并处理它。
                    failure.compareAndSet(null, exception);
                }
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return new IdempotencyLease(future, failure, active);
    }

    /**
     * 停止续期并在发生过续期失败时抛出该异常，重复关闭保持幂等。
     */
    @Override
    public void close() {
        synchronized (active) {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            // 与续期任务共用监视器，保证关闭返回后不会再有续期和完成操作并发执行。
            future.cancel(false);
            RuntimeException renewalFailure = failure.get();
            if (renewalFailure != null) {
                throw renewalFailure;
            }
        }
    }
}
