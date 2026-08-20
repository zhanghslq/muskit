package io.github.zhanghslq.muskit.observation;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可观测核心模型测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class ObservationCoreTest {

    /**
     * 验证标签不可变并只通过预定义标签键创建。
     */
    @Test
    void shouldCreateImmutableLowCardinalityTags() {
        ObservationTags original = ObservationTags.of(MuskitTagKey.POLICY, "order-create");
        ObservationTags extended = original.and(MuskitTagKey.OUTCOME, "success");

        assertThat(original.asMap()).containsOnlyKeys(MuskitTagKey.POLICY);
        assertThat(extended.asMap()).containsEntry(MuskitTagKey.OUTCOME, "success");
        assertThatThrownBy(() -> extended.asMap().put(MuskitTagKey.STATE, "open"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证过长标签值会被拒绝，避免意外写入无界业务内容。
     */
    @Test
    void shouldRejectOversizedTagValue() {
        assertThatThrownBy(() -> ObservationTags.of(MuskitTagKey.POLICY, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证空注册器接受全部指标类型且不产生副作用。
     */
    @Test
    void shouldProvideNoOpRegistry() {
        MuskitObservationRegistry registry = MuskitObservationRegistry.noop();

        assertThatCode(() -> {
            registry.increment(MuskitMetric.RETRY_ATTEMPT, ObservationTags.empty());
            registry.recordDuration(
                    MuskitMetric.CIRCUIT_BREAKER_CALL,
                    Duration.ofMillis(10),
                    ObservationTags.empty());
            registry.setGauge(MuskitMetric.OUTBOX_PENDING, 3D, ObservationTags.empty());
        }).doesNotThrowAnyException();
    }
}
