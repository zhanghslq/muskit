package io.github.zhanghslq.muskit.observation.micrometer;

import java.time.Duration;

import io.github.zhanghslq.muskit.observation.MuskitMetric;
import io.github.zhanghslq.muskit.observation.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.ObservationTags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Micrometer Muskit 指标适配器测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MicrometerMuskitObservationRegistryTest {

    /**
     * 验证计数器、耗时和 Gauge 都使用受控标签写入 Micrometer。
     */
    @Test
    void shouldRecordAllMetricKinds() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerMuskitObservationRegistry registry = new MicrometerMuskitObservationRegistry(meterRegistry);
        ObservationTags tags = ObservationTags.of(MuskitTagKey.POLICY, "order-create")
                .and(MuskitTagKey.OUTCOME, "success");

        registry.increment(MuskitMetric.RETRY_ATTEMPT, tags);
        registry.recordDuration(MuskitMetric.CIRCUIT_BREAKER_CALL, Duration.ofMillis(25), tags);
        registry.setGauge(MuskitMetric.OUTBOX_PENDING, 7D, ObservationTags.empty());
        registry.setGauge(MuskitMetric.OUTBOX_PENDING, 3D, ObservationTags.empty());

        assertThat(meterRegistry.get("muskit.retry.attempt").tags("policy", "order-create", "outcome", "success")
                .counter().count()).isEqualTo(1D);
        assertThat(meterRegistry.get("muskit.circuitbreaker.call").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(25D);
        assertThat(meterRegistry.get("muskit.outbox.pending").gauge().value()).isEqualTo(3D);
    }

    /**
     * 验证使用错误记录方式时明确失败。
     */
    @Test
    void shouldRejectMetricKindMismatch() {
        MicrometerMuskitObservationRegistry registry =
                new MicrometerMuskitObservationRegistry(new SimpleMeterRegistry());

        assertThatThrownBy(() -> registry.increment(MuskitMetric.OUTBOX_PENDING, ObservationTags.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
