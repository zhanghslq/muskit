package io.github.zhanghslq.muskit.observation.autoconfigure;

import io.github.zhanghslq.muskit.observation.autoconfigure.endpoint.MuskitEndpoint;
import io.github.zhanghslq.muskit.observation.micrometer.MicrometerMuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Muskit 统一可观测性自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitObservationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitObservationAutoConfiguration.class));

    /**
     * 验证存在 MeterRegistry 时创建统一指标注册器和 Endpoint。
     */
    @Test
    void shouldConfigureRegistryAndEndpoint() {
        contextRunner
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(MuskitObservationRegistry.class);
                    assertThat(context.getBean(MuskitObservationRegistry.class))
                            .isInstanceOf(MicrometerMuskitObservationRegistry.class);
                    assertThat(context).hasSingleBean(MuskitEndpoint.class);

                    context.getBean(MuskitObservationRegistry.class)
                            .increment(MuskitMetric.RETRY_ATTEMPT, ObservationTags.empty());
                    assertThat(context.getBean(SimpleMeterRegistry.class)
                            .get("muskit.retry.attempt").counter().count()).isEqualTo(1D);
                    assertThat(context.getBean(MuskitEndpoint.class).muskit())
                            .containsKey("components");
                });
    }

    /**
     * 验证使用者提供的指标注册器优先于默认实现。
     */
    @Test
    void shouldBackOffForUserRegistry() {
        MuskitObservationRegistry custom = MuskitObservationRegistry.noop();

        contextRunner
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(MuskitObservationRegistry.class, () -> custom)
                .run(context -> assertThat(context.getBean(MuskitObservationRegistry.class)).isSameAs(custom));
    }

    /**
     * 验证总开关关闭后不创建可观测组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("muskit.observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MuskitObservationRegistry.class);
                    assertThat(context).doesNotHaveBean(MuskitEndpoint.class);
                });
    }
}
