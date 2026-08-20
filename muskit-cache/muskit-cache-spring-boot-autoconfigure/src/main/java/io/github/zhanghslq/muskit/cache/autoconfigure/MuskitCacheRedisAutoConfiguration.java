package io.github.zhanghslq.muskit.cache.autoconfigure;

import io.github.zhanghslq.muskit.cache.CacheStore;
import io.github.zhanghslq.muskit.cache.redis.RedisCacheStore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 强依赖 Redis 的缓存 Provider 自动配置，后端异常不会切换为本地缓存。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@ConditionalOnClass({RedissonClient.class, RedisCacheStore.class})
@ConditionalOnProperty(prefix = "muskit.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "muskit.cache", name = "provider", havingValue = "redis", matchIfMissing = true)
@EnableConfigurationProperties(MuskitCacheProperties.class)
public class MuskitCacheRedisAutoConfiguration {

    /**
     * 创建 Redis 缓存自动配置。
     */
    public MuskitCacheRedisAutoConfiguration() {
    }

    /**
     * 创建 Redis 缓存存储。
     *
     * @param redissonClient Redisson 客户端
     * @param properties 缓存配置
     * @return Redis 缓存存储
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheStore muskitCacheStore(
            RedissonClient redissonClient,
            MuskitCacheProperties properties) {
        return new RedisCacheStore(redissonClient, properties.getKeyPrefix());
    }
}
