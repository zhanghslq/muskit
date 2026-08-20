package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.redis.RedisIdempotencyStore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Muskit Redis 幂等状态存储自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(before = MuskitIdempotencyAutoConfiguration.class)
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@ConditionalOnClass({RedissonClient.class, RedisIdempotencyStore.class})
@ConditionalOnProperty(
        prefix = "muskit.idempotency",
        name = "provider",
        havingValue = "redis",
        matchIfMissing = true)
@ConditionalOnProperty(prefix = "muskit.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitIdempotencyProperties.class)
public class MuskitIdempotencyRedisAutoConfiguration {

    /**
     * 创建 Redis 幂等自动配置。
     */
    public MuskitIdempotencyRedisAutoConfiguration() {
    }

    /**
     * 创建 Redis 幂等状态存储。
     *
     * @param redissonClient Redisson 客户端
     * @param properties 幂等配置属性
     * @return Redis 幂等状态存储
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore muskitRedisIdempotencyStore(
            RedissonClient redissonClient,
            MuskitIdempotencyProperties properties) {
        return new RedisIdempotencyStore(redissonClient, properties.getRedisKeyPrefix());
    }
}
