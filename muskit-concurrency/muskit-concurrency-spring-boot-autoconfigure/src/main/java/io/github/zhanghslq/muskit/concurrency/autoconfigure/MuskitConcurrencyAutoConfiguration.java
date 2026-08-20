package io.github.zhanghslq.muskit.concurrency.autoconfigure;

import io.github.zhanghslq.muskit.concurrency.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyPolicyResolver;
import io.github.zhanghslq.muskit.concurrency.LocalConcurrencyLimiter;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
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
 * Muskit 并发控制自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnProperty(prefix = "muskit.concurrency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitConcurrencyProperties.class)
public class MuskitConcurrencyAutoConfiguration {

    /**
     * 创建 Muskit 并发控制自动配置。
     */
    public MuskitConcurrencyAutoConfiguration() {
    }

    /**
     * 创建默认的进程内并发额度提供器。
     *
     * @return 本地并发额度提供器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.concurrency",
            name = "provider",
            havingValue = "local",
            matchIfMissing = true)
    public ConcurrencyLimiter muskitConcurrencyLimiter() {
        return new LocalConcurrencyLimiter();
    }

    /**
     * 创建基于配置属性的并发策略解析器。
     *
     * @param properties 并发控制配置属性
     * @return 并发策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public ConcurrencyPolicyResolver muskitConcurrencyPolicyResolver(MuskitConcurrencyProperties properties) {
        return new PropertiesConcurrencyPolicyResolver(properties);
    }

    /**
     * 创建并发控制注解切面。
     *
     * @param concurrencyLimiter 并发额度提供器
     * @param policyResolver 并发策略解析器
     * @param beanFactory Spring Bean 工厂
     * @param properties 并发控制配置属性
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 并发控制切面
     */
    @Bean
    @ConditionalOnMissingBean
    public ConcurrencyGuardAspect muskitConcurrencyGuardAspect(
            ConcurrencyLimiter concurrencyLimiter,
            ConcurrencyPolicyResolver policyResolver,
            BeanFactory beanFactory,
            MuskitConcurrencyProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new ConcurrencyGuardAspect(
                concurrencyLimiter,
                policyResolver,
                beanFactory,
                properties.getOrder(),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }
}
