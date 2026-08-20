package io.github.zhanghslq.muskit.concurrency.autoconfigure;

import io.github.zhanghslq.muskit.concurrency.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.redis.RedisConcurrencyLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 分布式并发额度自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(before = MuskitConcurrencyAutoConfiguration.class)
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@ConditionalOnClass({RedissonClient.class, RedisConcurrencyLimiter.class})
@ConditionalOnProperty(prefix = "muskit.concurrency", name = "provider", havingValue = "redis")
@ConditionalOnProperty(prefix = "muskit.concurrency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitConcurrencyProperties.class)
public class MuskitConcurrencyRedisAutoConfiguration {

    /**
     * 创建 Redis 分布式并发自动配置。
     */
    public MuskitConcurrencyRedisAutoConfiguration() {
    }

    /**
     * 创建 Redis 分布式并发额度提供器。
     *
     * @param redissonClient Redisson 客户端
     * @param properties 并发配置属性
     * @return Redis 分布式并发额度提供器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ConcurrencyLimiter.class)
    public ConcurrencyLimiter muskitRedisConcurrencyLimiter(
            RedissonClient redissonClient,
            MuskitConcurrencyProperties properties) {
        return new RedisConcurrencyLimiter(
                redissonClient,
                properties.getRedisKeyPrefix(),
                properties.getRedisLeaseTime());
    }
}
