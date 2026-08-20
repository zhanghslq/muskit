package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 与具体 Provider 无关的熔断策略和注解切面自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitCircuitBreakerResilience4jAutoConfiguration.class)
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnBean(CircuitBreaker.class)
@ConditionalOnProperty(
        prefix = "muskit.resilience",
        name = "circuit-breaker-enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MuskitResilienceProperties.class)
public class MuskitCircuitBreakerAutoConfiguration {

    /**
     * 创建熔断自动配置。
     */
    public MuskitCircuitBreakerAutoConfiguration() {
    }

    /**
     * 创建配置属性熔断策略解析器。
     *
     * @param properties 韧性配置属性
     * @return 熔断策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerPolicyResolver muskitCircuitBreakerPolicyResolver(
            MuskitResilienceProperties properties) {
        return new PropertiesCircuitBreakerPolicyResolver(properties);
    }

    /**
     * 创建熔断注解切面。
     *
     * @param circuitBreaker 熔断 Provider
     * @param policyResolver 熔断策略解析器
     * @param properties 韧性配置属性
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 熔断切面
     */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerGuardAspect muskitCircuitBreakerGuardAspect(
            CircuitBreaker circuitBreaker,
            CircuitBreakerPolicyResolver policyResolver,
            MuskitResilienceProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new CircuitBreakerGuardAspect(
                circuitBreaker,
                policyResolver,
                properties.getCircuitBreakerOrder(),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }
}
