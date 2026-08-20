package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.redis.RedisTokenBucketRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redis 分布式令牌桶限流自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(before = MuskitResilienceAutoConfiguration.class)
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@ConditionalOnClass({RedissonClient.class, RedisTokenBucketRateLimiter.class})
@ConditionalOnProperty(prefix = "muskit.resilience", name = "rate-limit-provider", havingValue = "redis")
@ConditionalOnProperty(
        prefix = "muskit.resilience",
        name = "rate-limit-enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MuskitResilienceProperties.class)
public class MuskitResilienceRedisAutoConfiguration {

    /**
     * 创建 Redis 限流自动配置。
     */
    public MuskitResilienceRedisAutoConfiguration() {
    }

    /**
     * 创建 Redis 分布式令牌桶 Provider。
     *
     * @param redissonClient Redisson 客户端
     * @param properties 韧性配置属性
     * @return Redis 限流 Provider
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter muskitRedisRateLimiter(
            RedissonClient redissonClient,
            MuskitResilienceProperties properties) {
        return new RedisTokenBucketRateLimiter(
                redissonClient,
                properties.getRedisRateLimitKeyPrefix());
    }
}
