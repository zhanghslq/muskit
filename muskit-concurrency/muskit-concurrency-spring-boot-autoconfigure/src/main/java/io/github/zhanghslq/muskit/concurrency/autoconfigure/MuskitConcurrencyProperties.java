package io.github.zhanghslq.muskit.concurrency.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.zhanghslq.muskit.concurrency.ConcurrencyScope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Muskit 并发控制配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.concurrency")
public class MuskitConcurrencyProperties {

    private boolean enabled = true;
    private int order = Ordered.HIGHEST_PRECEDENCE + 100;
    private ConcurrencyProviderType provider = ConcurrencyProviderType.LOCAL;
    private String redisKeyPrefix = "muskit:concurrency:";
    private Duration redisLeaseTime = Duration.ofSeconds(30);
    private Map<String, PolicyProperties> policies = new LinkedHashMap<>();

    /**
     * 创建 Muskit 并发控制配置属性。
     */
    public MuskitConcurrencyProperties() {
    }

    /**
     * 返回并发控制是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置并发控制是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回并发控制切面的执行顺序。
     *
     * @return 切面顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置并发控制切面的执行顺序。
     *
     * @param order 切面顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 返回并发额度 Provider 类型。
     *
     * @return Provider 类型
     */
    public ConcurrencyProviderType getProvider() {
        return provider;
    }

    /**
     * 设置并发额度 Provider 类型。
     *
     * @param provider Provider 类型
     */
    public void setProvider(ConcurrencyProviderType provider) {
        this.provider = provider;
    }

    /**
     * 返回 Redis 分布式额度键前缀。
     *
     * @return Redis 键前缀
     */
    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    /**
     * 设置 Redis 分布式额度键前缀。
     *
     * @param redisKeyPrefix Redis 键前缀
     */
    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /**
     * 返回 Redis 分布式额度失联租约时间。
     *
     * @return Redis 额度租约
     */
    public Duration getRedisLeaseTime() {
        return redisLeaseTime;
    }

    /**
     * 设置 Redis 分布式额度失联租约时间。
     *
     * @param redisLeaseTime Redis 额度租约
     */
    public void setRedisLeaseTime(Duration redisLeaseTime) {
        this.redisLeaseTime = redisLeaseTime;
    }

    /**
     * 返回按名称配置的并发策略。
     *
     * @return 并发策略配置
     */
    public Map<String, PolicyProperties> getPolicies() {
        return policies;
    }

    /**
     * 设置按名称配置的并发策略。
     *
     * @param policies 并发策略配置
     */
    public void setPolicies(Map<String, PolicyProperties> policies) {
        this.policies = policies == null ? new LinkedHashMap<>() : new LinkedHashMap<>(policies);
    }

    /**
     * 单个并发控制策略的配置属性。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class PolicyProperties {

        private int maxConcurrency;
        private Duration maxWait = Duration.ZERO;
        private ConcurrencyScope scope = ConcurrencyScope.GLOBAL;
        private boolean fair;

        /**
         * 创建单个并发控制策略配置。
         */
        public PolicyProperties() {
        }

        /**
         * 返回最大并发数。
         *
         * @return 最大并发数
         */
        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        /**
         * 设置最大并发数。
         *
         * @param maxConcurrency 最大并发数
         */
        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        /**
         * 返回获取额度的最大等待时间。
         *
         * @return 最大等待时间
         */
        public Duration getMaxWait() {
            return maxWait;
        }

        /**
         * 设置获取额度的最大等待时间。
         *
         * @param maxWait 最大等待时间
         */
        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }

        /**
         * 返回并发隔离范围。
         *
         * @return 并发隔离范围
         */
        public ConcurrencyScope getScope() {
            return scope;
        }

        /**
         * 设置并发隔离范围。
         *
         * @param scope 并发隔离范围
         */
        public void setScope(ConcurrencyScope scope) {
            this.scope = scope;
        }

        /**
         * 返回是否启用公平获取顺序。
         *
         * @return 是否公平
         */
        public boolean isFair() {
            return fair;
        }

        /**
         * 设置是否启用公平获取顺序。
         *
         * @param fair 是否公平
         */
        public void setFair(boolean fair) {
            this.fair = fair;
        }
    }
}
