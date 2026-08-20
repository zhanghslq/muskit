package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.jdbc.JdbcIdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Muskit JDBC 幂等状态存储自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(before = MuskitIdempotencyAutoConfiguration.class)
@ConditionalOnClass({JdbcOperations.class, JdbcIdempotencyStore.class})
@ConditionalOnProperty(prefix = "muskit.idempotency", name = "provider", havingValue = "jdbc")
@ConditionalOnProperty(prefix = "muskit.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitIdempotencyProperties.class)
public class MuskitIdempotencyJdbcAutoConfiguration {

    /**
     * 创建 JDBC 幂等自动配置。
     */
    public MuskitIdempotencyJdbcAutoConfiguration() {
    }

    /**
     * 创建 JDBC 幂等状态存储并按配置决定是否初始化表结构。
     *
     * @param jdbcOperations JDBC 操作接口
     * @param properties 幂等配置属性
     * @return JDBC 幂等状态存储
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore muskitJdbcIdempotencyStore(
            JdbcOperations jdbcOperations,
            MuskitIdempotencyProperties properties) {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(
                jdbcOperations, properties.getJdbcTableName());
        if (properties.isInitializeSchema()) {
            store.initializeSchema();
        }
        return store;
    }
}
