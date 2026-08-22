package io.github.zhanghslq.muskit.outbox.autoconfigure.jdbc;

import io.github.zhanghslq.muskit.outbox.autoconfigure.MuskitOutboxProperties;
import io.github.zhanghslq.muskit.outbox.jdbc.JdbcOutboxRepository;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * JDBC Outbox 存储自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({JdbcOperations.class, JdbcOutboxRepository.class})
@ConditionalOnBean(JdbcOperations.class)
@ConditionalOnProperty(prefix = "muskit.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitOutboxProperties.class)
public class MuskitOutboxJdbcAutoConfiguration {

    /**
     * 创建 JDBC Outbox 存储自动配置。
     */
    public MuskitOutboxJdbcAutoConfiguration() {
    }

    /**
     * 创建 JDBC Outbox 存储，并按配置选择是否初始化表结构。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param properties Outbox 配置属性
     * @return Outbox 存储
     */
    @Bean
    @ConditionalOnMissingBean(OutboxRepository.class)
    public OutboxRepository muskitOutboxRepository(
            JdbcOperations jdbcOperations,
            MuskitOutboxProperties properties) {
        JdbcOutboxRepository repository = new JdbcOutboxRepository(
                jdbcOperations,
                properties.getTableName(),
                properties.isRequireTransaction());
        if (properties.isInitializeSchema()) {
            repository.initializeSchema();
        }
        return repository;
    }
}
