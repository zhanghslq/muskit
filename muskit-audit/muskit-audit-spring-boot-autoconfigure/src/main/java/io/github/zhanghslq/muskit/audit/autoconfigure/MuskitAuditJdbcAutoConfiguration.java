package io.github.zhanghslq.muskit.audit.autoconfigure;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zhanghslq.muskit.audit.AuditWriter;
import io.github.zhanghslq.muskit.audit.jdbc.JdbcAuditWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * JDBC 审计 Writer 自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({DataSource.class, ObjectMapper.class, JdbcAuditWriter.class})
@ConditionalOnProperty(prefix = "muskit.audit", name = "provider", havingValue = "jdbc", matchIfMissing = true)
@EnableConfigurationProperties(MuskitAuditProperties.class)
public class MuskitAuditJdbcAutoConfiguration {

    /**
     * 创建 JDBC 审计自动配置。
     */
    public MuskitAuditJdbcAutoConfiguration() {
    }

    /**
     * 创建 JDBC 审计 Writer。
     *
     * @param dataSource JDBC 数据源
     * @param objectMapper JSON 编码器
     * @param properties 审计配置
     * @return 审计 Writer
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditWriter muskitAuditWriter(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MuskitAuditProperties properties) {
        return new JdbcAuditWriter(dataSource, objectMapper, properties.getTableName());
    }
}
