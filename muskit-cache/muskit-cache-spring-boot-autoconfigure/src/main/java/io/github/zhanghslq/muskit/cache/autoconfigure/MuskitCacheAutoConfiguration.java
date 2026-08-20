package io.github.zhanghslq.muskit.cache.autoconfigure;

import io.github.zhanghslq.muskit.cache.CachePolicyResolver;
import io.github.zhanghslq.muskit.cache.CacheStore;
import io.github.zhanghslq.muskit.cache.CacheTemplate;
import io.github.zhanghslq.muskit.cache.ReliableCache;
import io.github.zhanghslq.muskit.executor.ManagedExecutorRegistry;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 与具体后端无关的可靠缓存和策略自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitCacheRedisAutoConfiguration.class)
@ConditionalOnBean({CacheStore.class, ManagedExecutorRegistry.class})
@ConditionalOnProperty(prefix = "muskit.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitCacheProperties.class)
public class MuskitCacheAutoConfiguration {

    /**
     * 创建缓存主自动配置。
     */
    public MuskitCacheAutoConfiguration() {
    }

    /**
     * 创建配置属性缓存策略解析器。
     *
     * @param properties 缓存配置
     * @return 缓存策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CachePolicyResolver muskitCachePolicyResolver(MuskitCacheProperties properties) {
        return new PropertiesCachePolicyResolver(properties);
    }

    /**
     * 创建使用受管执行器刷新旧值的可靠缓存。
     *
     * @param store 缓存存储
     * @param executorRegistry 受管执行器注册表
     * @param properties 缓存配置
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 可靠缓存
     */
    @Bean
    @ConditionalOnMissingBean
    public ReliableCache muskitReliableCache(
            CacheStore store,
            ManagedExecutorRegistry executorRegistry,
            MuskitCacheProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new ReliableCache(
                store,
                executorRegistry.get(properties.getRefreshExecutor()),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop),
                java.time.Clock.systemUTC(),
                Math::random);
    }

    /**
     * 创建缓存业务模板。
     *
     * @param cache 可靠缓存
     * @param policyResolver 缓存策略解析器
     * @return 缓存模板
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheTemplate muskitCacheTemplate(
            ReliableCache cache,
            CachePolicyResolver policyResolver) {
        return new CacheTemplate(cache, policyResolver);
    }
}
