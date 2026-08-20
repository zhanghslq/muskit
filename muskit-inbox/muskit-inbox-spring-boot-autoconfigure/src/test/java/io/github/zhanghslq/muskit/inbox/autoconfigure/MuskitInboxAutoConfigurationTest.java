package io.github.zhanghslq.muskit.inbox.autoconfigure;

import javax.sql.DataSource;

import io.github.zhanghslq.muskit.inbox.InboxPolicyResolver;
import io.github.zhanghslq.muskit.inbox.InboxProcessor;
import io.github.zhanghslq.muskit.inbox.InboxStore;
import io.github.zhanghslq.muskit.inbox.InboxTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inbox JDBC 和主自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitInboxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    JdbcTestConfiguration.class,
                    MuskitInboxJdbcAutoConfiguration.class,
                    MuskitInboxAutoConfiguration.class);

    /**
     * 验证默认装配 JDBC 存储、策略、处理器和业务模板。
     */
    @Test
    void shouldConfigureJdbcInbox() {
        contextRunner.withPropertyValues("muskit.inbox.initialize-schema=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxStore.class);
                    assertThat(context).hasSingleBean(InboxPolicyResolver.class);
                    assertThat(context).hasSingleBean(InboxProcessor.class);
                    assertThat(context).hasSingleBean(InboxTemplate.class);
                });
    }

    /**
     * 验证显式禁用时不创建 Inbox Bean。
     */
    @Test
    void shouldDisableInbox() {
        contextRunner.withPropertyValues("muskit.inbox.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(InboxStore.class));
    }

    /**
     * Inbox 自动配置测试所需的内存 JDBC Bean。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    static class JdbcTestConfiguration {

        /**
         * 创建隔离的内存数据源。
         *
         * @return 测试数据源
         */
        @Bean
        DataSource inboxDataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:inbox-autoconfigure;DB_CLOSE_DELAY=-1", "sa", "");
        }

        /**
         * 创建 JDBC 操作接口。
         *
         * @param dataSource 测试数据源
         * @return JDBC 操作接口
         */
        @Bean
        JdbcOperations inboxJdbcOperations(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
