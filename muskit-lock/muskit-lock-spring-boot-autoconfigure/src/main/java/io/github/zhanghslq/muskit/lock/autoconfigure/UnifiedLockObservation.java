package io.github.zhanghslq.muskit.lock.autoconfigure;

import java.time.Duration;
import java.util.Objects;

import io.github.zhanghslq.muskit.observation.MuskitMetric;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.ObservationTags;

/**
 * 将锁获取和显式降级事件写入 Muskit 统一观测注册器。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class UnifiedLockObservation implements LockObservation {

    private final MuskitObservationRegistry observationRegistry;

    /**
     * 创建统一锁观测实现。
     *
     * @param observationRegistry 统一观测注册器
     */
    UnifiedLockObservation(MuskitObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
    }

    /**
     * 捕获锁获取开始时间，并在完成时记录耗时和低基数结果。
     *
     * @param lockName 低基数锁名称
     * @return 本次锁获取观测上下文
     */
    @Override
    public Acquisition start(String lockName) {
        long startedAt = System.nanoTime();
        ObservationTags tags = ObservationTags.of(MuskitTagKey.POLICY, lockName);
        return outcome -> observationRegistry.recordDuration(
                MuskitMetric.LOCK_ACQUIRE,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt)),
                tags.and(MuskitTagKey.OUTCOME, outcome.tagValue()));
    }

    /**
     * 记录一次业务显式允许的本地锁降级。
     *
     * @param lockName 低基数锁名称
     */
    @Override
    public void fallback(String lockName) {
        observationRegistry.increment(
                MuskitMetric.LOCK_FALLBACK,
                ObservationTags.of(MuskitTagKey.POLICY, lockName)
                        .and(MuskitTagKey.PROVIDER, "local-fallback")
                        .and(MuskitTagKey.OUTCOME, "activated"));
    }
}
