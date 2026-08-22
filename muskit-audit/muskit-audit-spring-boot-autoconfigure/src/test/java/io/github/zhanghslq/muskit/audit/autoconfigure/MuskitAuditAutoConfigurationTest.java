package io.github.zhanghslq.muskit.audit.autoconfigure;

import io.github.zhanghslq.muskit.audit.service.AuditRecorder;
import io.github.zhanghslq.muskit.audit.spi.AuditPrincipalProvider;
import io.github.zhanghslq.muskit.audit.spi.AuditWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitAuditAutoConfiguration.class))
            .withBean(AuditWriter.class, () -> event -> { });

    /**
     * 验证存在 Writer 时创建审计记录器。
     */
    @Test
    void shouldConfigureRecorder() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(AuditRecorder.class));
    }

    /**
     * 验证用户操作者 Provider 优先。
     */
    @Test
    void shouldBackOffForPrincipalProvider() {
        AuditPrincipalProvider provider = () -> java.util.Optional.of("custom");
        contextRunner.withBean(AuditPrincipalProvider.class, () -> provider)
                .run(context -> assertThat(context.getBean(AuditPrincipalProvider.class)).isSameAs(provider));
    }

    /**
     * 验证可显式禁用审计。
     */
    @Test
    void shouldDisableExplicitly() {
        contextRunner.withPropertyValues("muskit.audit.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AuditRecorder.class));
    }
}
