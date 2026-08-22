package io.github.zhanghslq.muskit.observation.spi;

import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import java.time.Duration;

/**
 * 未接入监控系统时使用的零副作用指标注册器。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class NoOpMuskitObservationRegistry implements MuskitObservationRegistry {

    static final NoOpMuskitObservationRegistry INSTANCE = new NoOpMuskitObservationRegistry();

    /**
     * 创建共享空实现。
     */
    private NoOpMuskitObservationRegistry() {
    }

    /**
     * 忽略计数器记录。
     *
     * @param metric 计数器指标
     * @param tags 低基数标签
     */
    @Override
    public void increment(MuskitMetric metric, ObservationTags tags) {
    }

    /**
     * 忽略耗时记录。
     *
     * @param metric 耗时指标
     * @param duration 调用耗时
     * @param tags 低基数标签
     */
    @Override
    public void recordDuration(MuskitMetric metric, Duration duration, ObservationTags tags) {
    }

    /**
     * 忽略当前值记录。
     *
     * @param metric 当前值指标
     * @param value 当前值
     * @param tags 低基数标签
     */
    @Override
    public void setGauge(MuskitMetric metric, double value, ObservationTags tags) {
    }
}
