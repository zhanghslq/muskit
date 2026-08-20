package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.resilience.ratelimit.LocalTokenBucketRateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Muskit 限流、SingleFlight 与 Deadline 能力自动配置入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnProperty(
        prefix = "muskit.resilience",
        name = "rate-limit-enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MuskitResilienceProperties.class)
public class MuskitResilienceAutoConfiguration {

    /**
     * 创建 Muskit 韧性自动配置。
     */
    public MuskitResilienceAutoConfiguration() {
    }

    /**
     * 创建默认本地令牌桶限流 Provider。
     *
     * @param properties 韧性配置属性
     * @return 本地令牌桶限流 Provider
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiter muskitRateLimiter(MuskitResilienceProperties properties) {
        return new LocalTokenBucketRateLimiter(
                properties.getMaxLocalBuckets(),
                properties.getLocalBucketIdleRetention());
    }

    /**
     * 创建配置属性限流策略解析器。
     *
     * @param properties 韧性配置属性
     * @return 限流策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitPolicyResolver muskitRateLimitPolicyResolver(MuskitResilienceProperties properties) {
        return new PropertiesRateLimitPolicyResolver(properties);
    }

    /**
     * 创建限流注解切面。
     *
     * @param rateLimiter 限流 Provider
     * @param policyResolver 限流策略解析器
     * @param beanFactory Spring Bean 工厂
     * @param properties 韧性配置属性
     * @return 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitGuardAspect muskitRateLimitGuardAspect(
            RateLimiter rateLimiter,
            RateLimitPolicyResolver policyResolver,
            BeanFactory beanFactory,
            MuskitResilienceProperties properties) {
        return new RateLimitGuardAspect(
                rateLimiter, policyResolver, beanFactory, properties.getRateLimitOrder());
    }
}
