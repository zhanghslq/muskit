package io.github.zhanghslq.muskit.observation.micrometer;

import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitMetricKind;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 Muskit 稳定指标目录写入 Micrometer 的适配器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MicrometerMuskitObservationRegistry implements MuskitObservationRegistry {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<GaugeKey, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

    /**
     * 创建 Micrometer 指标注册器。
     *
     * @param meterRegistry Micrometer 注册器
     */
    public MicrometerMuskitObservationRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry 不能为空");
    }

    /**
     * 增加一次计数器。
     *
     * @param metric 计数器指标
     * @param tags 低基数标签
     */
    @Override
    public void increment(MuskitMetric metric, ObservationTags tags) {
        requireKind(metric, MuskitMetricKind.COUNTER);
        Counter.builder(metric.metricName())
                .tags(toMicrometerTags(tags))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录一次非负耗时。
     *
     * @param metric 耗时指标
     * @param duration 调用耗时
     * @param tags 低基数标签
     */
    @Override
    public void recordDuration(MuskitMetric metric, Duration duration, ObservationTags tags) {
        requireKind(metric, MuskitMetricKind.TIMER);
        Objects.requireNonNull(duration, "指标耗时不能为空");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("指标耗时不能为负数");
        }
        Timer.builder(metric.metricName())
                .tags(toMicrometerTags(tags))
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * 创建或更新指定标签组合的 Gauge。
     *
     * @param metric 当前值指标
     * @param value 当前值
     * @param tags 低基数标签
     */
    @Override
    public void setGauge(MuskitMetric metric, double value, ObservationTags tags) {
        requireKind(metric, MuskitMetricKind.GAUGE);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Gauge 当前值必须是有限数字");
        }
        GaugeKey key = new GaugeKey(metric, Objects.requireNonNull(tags, "指标标签不能为空"));
        AtomicReference<Double> state = gauges.computeIfAbsent(key, ignored -> registerGauge(key));
        state.set(value);
    }

    /**
     * 注册 Gauge 的可变数值载体。
     *
     * @param key Gauge 唯一键
     * @return 数值载体
     */
    private AtomicReference<Double> registerGauge(GaugeKey key) {
        AtomicReference<Double> state = new AtomicReference<>(0D);
        Gauge.builder(key.metric().metricName(), state, AtomicReference::get)
                .tags(toMicrometerTags(key.tags()))
                .register(meterRegistry);
        return state;
    }

    /**
     * 校验指标使用方式与目录定义一致。
     *
     * @param metric 指标
     * @param expected 期望类型
     */
    private void requireKind(MuskitMetric metric, MuskitMetricKind expected) {
        Objects.requireNonNull(metric, "Muskit 指标不能为空");
        if (metric.kind() != expected) {
            throw new IllegalArgumentException("指标 " + metric.metricName() + " 不是 " + expected + " 类型");
        }
    }

    /**
     * 将受控标签转换为 Micrometer 标签。
     *
     * @param tags Muskit 标签
     * @return Micrometer 标签
     */
    private Iterable<Tag> toMicrometerTags(ObservationTags tags) {
        Objects.requireNonNull(tags, "指标标签不能为空");
        List<Tag> converted = new ArrayList<>(tags.asMap().size());
        tags.asMap().forEach((key, value) -> converted.add(Tag.of(key.tagName(), value)));
        return List.copyOf(converted);
    }

    /**
     * 唯一标识一个带标签的 Gauge。
     *
     * @param metric 指标
     * @param tags 标签
     * @author zhs
     * @since 2026-08-20
     */
    private record GaugeKey(MuskitMetric metric, ObservationTags tags) {
    }
}
