package io.github.zhanghslq.muskit.outbox.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit Transactional Outbox 配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.outbox")
public class MuskitOutboxProperties {

    private boolean enabled = true;
    private String tableName = "muskit_outbox";
    private boolean initializeSchema;
    private boolean requireTransaction = true;
    private int batchSize = 100;
    private Duration leaseTime = Duration.ofSeconds(30);
    private Duration retryDelay = Duration.ofSeconds(5);
    private int maxAttempts = 10;
    private double retryMultiplier = 2D;
    private Duration maxRetryDelay = Duration.ofMinutes(5);
    private boolean schedulerEnabled = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration publishedRetention = Duration.ofDays(7);

    /**
     * 创建 Outbox 配置属性。
     */
    public MuskitOutboxProperties() {
    }

    /**
     * 返回 Outbox 是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 Outbox 是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Outbox 表名。
     *
     * @return 表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置 Outbox 表名。
     *
     * @param tableName 表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 返回是否自动创建表结构。
     *
     * @return 是否自动创建表结构
     */
    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    /**
     * 设置是否自动创建表结构。
     *
     * @param initializeSchema 是否自动创建表结构
     */
    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    /**
     * 返回追加事件时是否强制要求活动事务。
     *
     * @return 是否要求活动事务
     */
    public boolean isRequireTransaction() {
        return requireTransaction;
    }

    /**
     * 设置追加事件时是否强制要求活动事务。
     *
     * @param requireTransaction 是否要求活动事务
     */
    public void setRequireTransaction(boolean requireTransaction) {
        this.requireTransaction = requireTransaction;
    }

    /**
     * 返回每次发布的最大事件数。
     *
     * @return 批量大小
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 设置每次发布的最大事件数。
     *
     * @param batchSize 批量大小
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * 返回发布租约时间。
     *
     * @return 租约时间
     */
    public Duration getLeaseTime() {
        return leaseTime;
    }

    /**
     * 设置发布租约时间。
     *
     * @param leaseTime 租约时间
     */
    public void setLeaseTime(Duration leaseTime) {
        this.leaseTime = leaseTime;
    }

    /**
     * 返回失败后的重试等待时间。
     *
     * @return 重试等待时间
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * 设置失败后的重试等待时间。
     *
     * @param retryDelay 重试等待时间
     */
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    /**
     * 返回最大发布尝试次数。
     *
     * @return 最大发布尝试次数
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * 设置最大发布尝试次数。
     *
     * @param maxAttempts 最大发布尝试次数
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * 返回重试指数退避倍数。
     *
     * @return 退避倍数
     */
    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    /**
     * 设置重试指数退避倍数。
     *
     * @param retryMultiplier 退避倍数
     */
    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    /**
     * 返回最大重试等待时间。
     *
     * @return 最大重试等待时间
     */
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    /**
     * 设置最大重试等待时间。
     *
     * @param maxRetryDelay 最大重试等待时间
     */
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    /**
     * 返回后台轮询器是否启用。
     *
     * @return 是否启用轮询器
     */
    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    /**
     * 设置后台轮询器是否启用。
     *
     * @param schedulerEnabled 是否启用轮询器
     */
    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    /**
     * 返回后台轮询间隔。
     *
     * @return 轮询间隔
     */
    public Duration getPollInterval() {
        return pollInterval;
    }

    /**
     * 设置后台轮询间隔。
     *
     * @param pollInterval 轮询间隔
     */
    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    /**
     * 返回已发布事件保留时间。
     *
     * @return 保留时间
     */
    public Duration getPublishedRetention() {
        return publishedRetention;
    }

    /**
     * 设置已发布事件保留时间。
     *
     * @param publishedRetention 保留时间
     */
    public void setPublishedRetention(Duration publishedRetention) {
        this.publishedRetention = publishedRetention;
    }
}
