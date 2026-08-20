package io.github.zhanghslq.muskit.inbox.autoconfigure;

import io.github.zhanghslq.muskit.inbox.InboxStore;
import io.github.zhanghslq.muskit.inbox.jdbc.JdbcInboxStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * JDBC Inbox Provider 自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({JdbcOperations.class, JdbcInboxStore.class})
@ConditionalOnProperty(prefix = "muskit.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "muskit.inbox", name = "provider", havingValue = "jdbc", matchIfMissing = true)
@EnableConfigurationProperties(MuskitInboxProperties.class)
public class MuskitInboxJdbcAutoConfiguration {

    /**
     * 创建 JDBC Inbox 自动配置。
     */
    public MuskitInboxJdbcAutoConfiguration() {
    }

    /**
     * 创建 JDBC Inbox 存储并按配置初始化表结构。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param properties Inbox 配置
     * @return Inbox 存储
     */
    @Bean
    @ConditionalOnMissingBean
    public InboxStore muskitInboxStore(
            JdbcOperations jdbcOperations,
            MuskitInboxProperties properties) {
        JdbcInboxStore store = new JdbcInboxStore(jdbcOperations, properties.getTableName());
        if (properties.isInitializeSchema()) {
            store.initializeSchema();
        }
        return store;
    }
}
