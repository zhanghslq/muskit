package io.github.zhanghslq.muskit.resilience.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Muskit 韧性能力配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.resilience")
public class MuskitResilienceProperties {

    private boolean rateLimitEnabled = true;
    private int rateLimitOrder = Ordered.HIGHEST_PRECEDENCE + 120;
    private int maxLocalBuckets = 100_000;
    private Duration localBucketIdleRetention = Duration.ofMinutes(10);
    private Map<String, RateLimitPolicyProperties> rateLimitPolicies = new LinkedHashMap<>();

    /**
     * 创建 Muskit 韧性配置属性。
     */
    public MuskitResilienceProperties() {
    }

    /**
     * 返回注解限流是否启用。
     *
     * @return 是否启用
     */
    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    /**
     * 设置注解限流是否启用。
     *
     * @param rateLimitEnabled 是否启用
     */
    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    /**
     * 返回限流切面顺序。
     *
     * @return 切面顺序
     */
    public int getRateLimitOrder() {
        return rateLimitOrder;
    }

    /**
     * 设置限流切面顺序。
     *
     * @param rateLimitOrder 切面顺序
     */
    public void setRateLimitOrder(int rateLimitOrder) {
        this.rateLimitOrder = rateLimitOrder;
    }

    /**
     * 返回本地限流桶数量上限。
     *
     * @return 桶数量上限
     */
    public int getMaxLocalBuckets() {
        return maxLocalBuckets;
    }

    /**
     * 设置本地限流桶数量上限。
     *
     * @param maxLocalBuckets 桶数量上限
     */
    public void setMaxLocalBuckets(int maxLocalBuckets) {
        this.maxLocalBuckets = maxLocalBuckets;
    }

    /**
     * 返回本地空闲限流桶保留时间。
     *
     * @return 空闲保留时间
     */
    public Duration getLocalBucketIdleRetention() {
        return localBucketIdleRetention;
    }

    /**
     * 设置本地空闲限流桶保留时间。
     *
     * @param localBucketIdleRetention 空闲保留时间
     */
    public void setLocalBucketIdleRetention(Duration localBucketIdleRetention) {
        this.localBucketIdleRetention = localBucketIdleRetention;
    }

    /**
     * 返回按名称配置的限流策略。
     *
     * @return 限流策略映射
     */
    public Map<String, RateLimitPolicyProperties> getRateLimitPolicies() {
        return rateLimitPolicies;
    }

    /**
     * 设置按名称配置的限流策略。
     *
     * @param rateLimitPolicies 限流策略映射
     */
    public void setRateLimitPolicies(Map<String, RateLimitPolicyProperties> rateLimitPolicies) {
        this.rateLimitPolicies = rateLimitPolicies == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(rateLimitPolicies);
    }

    /**
     * 单个令牌桶限流策略配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class RateLimitPolicyProperties {

        private int capacity;
        private int refillTokens;
        private Duration refillPeriod = Duration.ofSeconds(1);
        private RateLimitScope scope = RateLimitScope.GLOBAL;

        /**
         * 创建限流策略配置。
         */
        public RateLimitPolicyProperties() {
        }

        /**
         * 返回令牌桶容量。
         *
         * @return 令牌桶容量
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * 设置令牌桶容量。
         *
         * @param capacity 令牌桶容量
         */
        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 返回每周期补充令牌数。
         *
         * @return 补充令牌数
         */
        public int getRefillTokens() {
            return refillTokens;
        }

        /**
         * 设置每周期补充令牌数。
         *
         * @param refillTokens 补充令牌数
         */
        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        /**
         * 返回令牌补充周期。
         *
         * @return 补充周期
         */
        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        /**
         * 设置令牌补充周期。
         *
         * @param refillPeriod 补充周期
         */
        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        /**
         * 返回限流隔离范围。
         *
         * @return 隔离范围
         */
        public RateLimitScope getScope() {
            return scope;
        }

        /**
         * 设置限流隔离范围。
         *
         * @param scope 隔离范围
         */
        public void setScope(RateLimitScope scope) {
            this.scope = scope;
        }
    }
}
