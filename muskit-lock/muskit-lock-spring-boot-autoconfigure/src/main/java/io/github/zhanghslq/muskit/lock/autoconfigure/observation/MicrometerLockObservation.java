package io.github.zhanghslq.muskit.lock.autoconfigure.observation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

/**
 * 使用 Micrometer 记录分布式锁耗时、结果和本地降级次数。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MicrometerLockObservation implements LockObservation {

    /** 锁获取耗时指标名称。 */
    public static final String ACQUIRE_METRIC_NAME = "muskit.lock.acquire";
    /** Redis 锁降级为本地锁的次数指标名称。 */
    public static final String FALLBACK_METRIC_NAME = "muskit.lock.fallback";

    private final MeterRegistry meterRegistry;

    /**
     * 创建 Micrometer 锁指标实现。
     *
     * @param meterRegistry 指标注册表
     */
    public MicrometerLockObservation(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry 不能为空");
    }

    /**
     * 开始记录 Redis 优先锁获取耗时。
     *
     * @param lockName 低基数锁名称
     * @return 锁获取指标上下文
     */
    @Override
    public Acquisition start(String lockName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return outcome -> sample.stop(Timer.builder(ACQUIRE_METRIC_NAME)
                .description("Muskit 分布式锁获取耗时")
                .tag("lock", lockName)
                .tag("provider", "redis-first")
                .tag("outcome", outcome.tagValue())
                .register(meterRegistry));
    }

    /**
     * 记录一次显式允许的本地锁降级事件。
     *
     * @param lockName 低基数锁名称
     */
    @Override
    public void fallback(String lockName) {
        Counter.builder(FALLBACK_METRIC_NAME)
                .description("Muskit Redis 分布式锁降级到本地锁的次数")
                .tag("lock", lockName)
                .tag("provider", "local-fallback")
                .tag("outcome", "activated")
                .register(meterRegistry)
                .increment();
    }
}
