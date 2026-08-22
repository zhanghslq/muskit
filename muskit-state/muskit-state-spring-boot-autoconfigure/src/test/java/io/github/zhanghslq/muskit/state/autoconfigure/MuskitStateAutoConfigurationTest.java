package io.github.zhanghslq.muskit.state.autoconfigure;

import io.github.zhanghslq.muskit.state.machine.StateMachineFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状态机自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitStateAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitStateAutoConfiguration.class));

    /**
     * 验证默认创建状态机工厂。
     */
    @Test
    void shouldConfigureFactoryByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(StateMachineFactory.class));
    }

    /**
     * 验证用户工厂优先。
     */
    @Test
    void shouldBackOffForUserFactory() {
        StateMachineFactory custom = new StateMachineFactory(0);
        contextRunner.withBean(StateMachineFactory.class, () -> custom)
                .run(context -> assertThat(context.getBean(StateMachineFactory.class)).isSameAs(custom));
    }

    /**
     * 验证可显式禁用状态机。
     */
    @Test
    void shouldDisableExplicitly() {
        contextRunner.withPropertyValues("muskit.state.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(StateMachineFactory.class));
    }
}
