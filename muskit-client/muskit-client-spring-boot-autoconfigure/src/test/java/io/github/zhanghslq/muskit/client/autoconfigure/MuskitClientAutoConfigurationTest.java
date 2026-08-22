package io.github.zhanghslq.muskit.client.autoconfigure;

import io.github.zhanghslq.muskit.client.model.ClientPropagationPolicy;
import io.github.zhanghslq.muskit.client.service.ClientPropagation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端调用链自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitClientAutoConfiguration.class));

    /**
     * 验证默认创建传播器。
     */
    @Test
    void shouldConfigurePropagationByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ClientPropagation.class));
    }

    /**
     * 验证用户提供的传播策略优先。
     */
    @Test
    void shouldBackOffForUserPolicy() {
        ClientPropagationPolicy custom = new ClientPropagationPolicy(
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2), 16, java.util.Map.of());
        contextRunner.withBean(ClientPropagationPolicy.class, () -> custom)
                .run(context -> assertThat(context.getBean(ClientPropagationPolicy.class)).isSameAs(custom));
    }

    /**
     * 验证可显式禁用客户端治理。
     */
    @Test
    void shouldDisableExplicitly() {
        contextRunner.withPropertyValues("muskit.client.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ClientPropagation.class));
    }
}
