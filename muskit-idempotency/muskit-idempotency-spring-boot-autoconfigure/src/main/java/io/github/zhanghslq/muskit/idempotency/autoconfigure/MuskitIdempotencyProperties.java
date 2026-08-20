package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Muskit 幂等状态配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.idempotency")
public class MuskitIdempotencyProperties {

    private boolean enabled = true;
    private int order = Ordered.HIGHEST_PRECEDENCE + 60;
    private IdempotencyProviderType provider = IdempotencyProviderType.REDIS;
    private String redisKeyPrefix = "muskit:idempotency:";
    private String jdbcTableName = "muskit_idempotency";
    private boolean initializeSchema;

    /**
     * 创建 Muskit 幂等配置属性。
     */
    public MuskitIdempotencyProperties() {
    }

    /**
     * 返回幂等控制是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置幂等控制是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回幂等切面顺序。
     *
     * @return 切面顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置幂等切面顺序。
     *
     * @param order 切面顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 返回状态存储 Provider 类型。
     *
     * @return Provider 类型
     */
    public IdempotencyProviderType getProvider() {
        return provider;
    }

    /**
     * 设置状态存储 Provider 类型。
     *
     * @param provider Provider 类型
     */
    public void setProvider(IdempotencyProviderType provider) {
        this.provider = provider;
    }

    /**
     * 返回 Redis 幂等键前缀。
     *
     * @return Redis 键前缀
     */
    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    /**
     * 设置 Redis 幂等键前缀。
     *
     * @param redisKeyPrefix Redis 键前缀
     */
    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /**
     * 返回 JDBC 幂等状态表名。
     *
     * @return JDBC 表名
     */
    public String getJdbcTableName() {
        return jdbcTableName;
    }

    /**
     * 设置 JDBC 幂等状态表名。
     *
     * @param jdbcTableName JDBC 表名
     */
    public void setJdbcTableName(String jdbcTableName) {
        this.jdbcTableName = jdbcTableName;
    }

    /**
     * 返回是否自动创建 JDBC 幂等状态表。
     *
     * @return 是否自动建表
     */
    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    /**
     * 设置是否自动创建 JDBC 幂等状态表。
     *
     * @param initializeSchema 是否自动建表
     */
    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }
}
