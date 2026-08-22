package io.github.zhanghslq.muskit.cache.autoconfigure;

import io.github.zhanghslq.muskit.cache.model.CacheFailureMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit Redis 可靠缓存配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.cache")
public class MuskitCacheProperties {

    private boolean enabled = true;
    private String provider = "redis";
    private String keyPrefix = "muskit:cache";
    private String refreshExecutor = "default";
    private Map<String, PolicyProperties> policies = defaultPolicies();

    /**
     * 创建默认缓存配置。
     */
    public MuskitCacheProperties() {
    }

    /** 返回是否启用缓存。 @return 是否启用 */
    public boolean isEnabled() { return enabled; }

    /** 设置是否启用缓存。 @param enabled 是否启用 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 返回缓存 Provider。 @return Provider 名称 */
    public String getProvider() { return provider; }

    /** 设置缓存 Provider。 @param provider Provider 名称 */
    public void setProvider(String provider) { this.provider = provider; }

    /** 返回 Redis Key 前缀。 @return Key 前缀 */
    public String getKeyPrefix() { return keyPrefix; }

    /** 设置 Redis Key 前缀。 @param keyPrefix Key 前缀 */
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    /** 返回旧值刷新执行器名称。 @return 执行器名称 */
    public String getRefreshExecutor() { return refreshExecutor; }

    /** 设置旧值刷新执行器名称。 @param refreshExecutor 执行器名称 */
    public void setRefreshExecutor(String refreshExecutor) { this.refreshExecutor = refreshExecutor; }

    /** 返回缓存策略配置。 @return 策略配置 */
    public Map<String, PolicyProperties> getPolicies() { return policies; }

    /** 设置缓存策略配置。 @param policies 策略配置 */
    public void setPolicies(Map<String, PolicyProperties> policies) {
        this.policies = new LinkedHashMap<>(policies);
    }

    /**
     * 创建默认缓存策略。
     *
     * @return 默认策略映射
     */
    private static Map<String, PolicyProperties> defaultPolicies() {
        Map<String, PolicyProperties> defaults = new LinkedHashMap<>();
        defaults.put("default", new PolicyProperties());
        return defaults;
    }

    /**
     * 单个缓存策略的可绑定配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class PolicyProperties {

        private Duration ttl = Duration.ofMinutes(10);
        private Duration nullTtl = Duration.ofSeconds(30);
        private double ttlJitterRatio = 0.1D;
        private Duration staleWhileRevalidate = Duration.ZERO;
        private CacheFailureMode failureMode = CacheFailureMode.FAIL_FAST;

        /** 创建默认缓存策略配置。 */
        public PolicyProperties() { }

        /** 返回正常值 TTL。 @return TTL */
        public Duration getTtl() { return ttl; }

        /** 设置正常值 TTL。 @param ttl TTL */
        public void setTtl(Duration ttl) { this.ttl = ttl; }

        /** 返回空值 TTL。 @return 空值 TTL */
        public Duration getNullTtl() { return nullTtl; }

        /** 设置空值 TTL。 @param nullTtl 空值 TTL */
        public void setNullTtl(Duration nullTtl) { this.nullTtl = nullTtl; }

        /** 返回 TTL 抖动比例。 @return 抖动比例 */
        public double getTtlJitterRatio() { return ttlJitterRatio; }

        /** 设置 TTL 抖动比例。 @param ttlJitterRatio 抖动比例 */
        public void setTtlJitterRatio(double ttlJitterRatio) { this.ttlJitterRatio = ttlJitterRatio; }

        /** 返回旧值刷新窗口。 @return 旧值刷新窗口 */
        public Duration getStaleWhileRevalidate() { return staleWhileRevalidate; }

        /** 设置旧值刷新窗口。 @param staleWhileRevalidate 旧值刷新窗口 */
        public void setStaleWhileRevalidate(Duration staleWhileRevalidate) {
            this.staleWhileRevalidate = staleWhileRevalidate;
        }

        /** 返回后端失败模式。 @return 失败模式 */
        public CacheFailureMode getFailureMode() { return failureMode; }

        /** 设置后端失败模式。 @param failureMode 失败模式 */
        public void setFailureMode(CacheFailureMode failureMode) { this.failureMode = failureMode; }
    }
}
