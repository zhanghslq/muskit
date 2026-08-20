package io.github.zhanghslq.muskit.inbox.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit JDBC Inbox 配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.inbox")
public class MuskitInboxProperties {

    private boolean enabled = true;
    private String provider = "jdbc";
    private String tableName = "muskit_inbox";
    private boolean initializeSchema;
    private Map<String, PolicyProperties> policies = defaultPolicies();

    /**
     * 创建默认 Inbox 配置。
     */
    public MuskitInboxProperties() {
    }

    /**
     * 返回是否启用 Inbox。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Inbox。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Inbox Provider 名称。
     *
     * @return Provider 名称
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置 Inbox Provider 名称。
     *
     * @param provider Provider 名称
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 返回 Inbox 表名。
     *
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置 Inbox 表名。
     *
     * @param tableName 表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 返回是否自动初始化表结构。
     *
     * @return 是否初始化
     */
    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    /**
     * 设置是否自动初始化表结构。
     *
     * @param initializeSchema 是否初始化
     */
    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    /**
     * 返回按名称索引的 Inbox 策略配置。
     *
     * @return 策略配置
     */
    public Map<String, PolicyProperties> getPolicies() {
        return policies;
    }

    /**
     * 设置按名称索引的 Inbox 策略配置。
     *
     * @param policies 策略配置
     */
    public void setPolicies(Map<String, PolicyProperties> policies) {
        this.policies = new LinkedHashMap<>(policies);
    }

    /**
     * 创建默认策略映射。
     *
     * @return 默认策略
     */
    private static Map<String, PolicyProperties> defaultPolicies() {
        Map<String, PolicyProperties> defaults = new LinkedHashMap<>();
        defaults.put("default", new PolicyProperties());
        return defaults;
    }

    /**
     * 单个 Inbox 策略的可绑定配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class PolicyProperties {

        private Duration processingTimeout = Duration.ofSeconds(30);
        private Duration retention = Duration.ofDays(7);
        private int maxAttempts = 5;
        private Duration initialRetryDelay = Duration.ofSeconds(1);
        private double retryMultiplier = 2D;
        private Duration maxRetryDelay = Duration.ofMinutes(5);

        /**
         * 创建默认策略配置。
         */
        public PolicyProperties() {
        }

        /** 返回处理租约时间。 @return 处理租约 */
        public Duration getProcessingTimeout() { return processingTimeout; }

        /** 设置处理租约时间。 @param processingTimeout 处理租约 */
        public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }

        /** 返回成功状态保留时间。 @return 保留时间 */
        public Duration getRetention() { return retention; }

        /** 设置成功状态保留时间。 @param retention 保留时间 */
        public void setRetention(Duration retention) { this.retention = retention; }

        /** 返回最大处理次数。 @return 最大处理次数 */
        public int getMaxAttempts() { return maxAttempts; }

        /** 设置最大处理次数。 @param maxAttempts 最大处理次数 */
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        /** 返回首次重试等待时间。 @return 首次等待时间 */
        public Duration getInitialRetryDelay() { return initialRetryDelay; }

        /** 设置首次重试等待时间。 @param initialRetryDelay 首次等待时间 */
        public void setInitialRetryDelay(Duration initialRetryDelay) { this.initialRetryDelay = initialRetryDelay; }

        /** 返回重试倍数。 @return 重试倍数 */
        public double getRetryMultiplier() { return retryMultiplier; }

        /** 设置重试倍数。 @param retryMultiplier 重试倍数 */
        public void setRetryMultiplier(double retryMultiplier) { this.retryMultiplier = retryMultiplier; }

        /** 返回最大重试等待时间。 @return 最大等待时间 */
        public Duration getMaxRetryDelay() { return maxRetryDelay; }

        /** 设置最大重试等待时间。 @param maxRetryDelay 最大等待时间 */
        public void setMaxRetryDelay(Duration maxRetryDelay) { this.maxRetryDelay = maxRetryDelay; }
    }
}
