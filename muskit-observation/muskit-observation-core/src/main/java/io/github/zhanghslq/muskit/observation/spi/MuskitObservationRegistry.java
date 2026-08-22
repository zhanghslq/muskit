package io.github.zhanghslq.muskit.observation.spi;

import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import java.time.Duration;

/**
 * 记录 Muskit 统一指标的基础设施无关 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface MuskitObservationRegistry {

    /**
     * 返回共享空实现。
     *
     * @return 空指标注册器
     */
    static MuskitObservationRegistry noop() {
        return NoOpMuskitObservationRegistry.INSTANCE;
    }

    /**
     * 增加一次计数器。
     *
     * @param metric 计数器指标
     * @param tags 低基数标签
     */
    void increment(MuskitMetric metric, ObservationTags tags);

    /**
     * 记录一次调用耗时。
     *
     * @param metric 耗时指标
     * @param duration 调用耗时
     * @param tags 低基数标签
     */
    void recordDuration(MuskitMetric metric, Duration duration, ObservationTags tags);

    /**
     * 设置当前值指标。
     *
     * @param metric 当前值指标
     * @param value 当前值
     * @param tags 低基数标签
     */
    void setGauge(MuskitMetric metric, double value, ObservationTags tags);
}
