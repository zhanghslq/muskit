package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.resilience.autoconfigure.ratelimit.PropertiesRateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.autoconfigure.ratelimit.RateLimitGuardAspect;
import io.github.zhanghslq.muskit.resilience.autoconfigure.retry.PropertiesRetryPolicyResolver;
import io.github.zhanghslq.muskit.resilience.autoconfigure.retry.RetryGuardAspect;
import io.github.zhanghslq.muskit.resilience.ratelimit.LocalTokenBucketRateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import io.github.zhanghslq.muskit.resilience.retry.RetryExecutor;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "rate-limit-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "rate-limit-provider",
            havingValue = "local",
            matchIfMissing = true)
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
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "rate-limit-enabled",
            havingValue = "true",
            matchIfMissing = true)
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
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "rate-limit-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RateLimitGuardAspect muskitRateLimitGuardAspect(
            RateLimiter rateLimiter,
            RateLimitPolicyResolver policyResolver,
            BeanFactory beanFactory,
            MuskitResilienceProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new RateLimitGuardAspect(
                rateLimiter,
                policyResolver,
                beanFactory,
                properties.getRateLimitOrder(),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }

    /**
     * 创建默认 Deadline 感知重试执行器。
     *
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 重试执行器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "retry-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RetryExecutor muskitRetryExecutor(
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new RetryExecutor(
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }

    /**
     * 创建配置属性重试策略解析器。
     *
     * @param properties 韧性配置属性
     * @return 重试策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "retry-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RetryPolicyResolver muskitRetryPolicyResolver(MuskitResilienceProperties properties) {
        return new PropertiesRetryPolicyResolver(properties);
    }

    /**
     * 创建重试注解切面。
     *
     * @param retryExecutor 重试执行器
     * @param policyResolver 重试策略解析器
     * @param properties 韧性配置属性
     * @return 重试切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.resilience",
            name = "retry-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RetryGuardAspect muskitRetryGuardAspect(
            RetryExecutor retryExecutor,
            RetryPolicyResolver policyResolver,
            MuskitResilienceProperties properties) {
        return new RetryGuardAspect(retryExecutor, policyResolver, properties.getRetryOrder());
    }
}
