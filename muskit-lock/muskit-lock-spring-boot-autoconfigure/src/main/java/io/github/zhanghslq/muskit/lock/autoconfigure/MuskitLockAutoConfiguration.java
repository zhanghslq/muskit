package io.github.zhanghslq.muskit.lock.autoconfigure;

import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.lock.LocalDistributedLockProvider;
import io.github.zhanghslq.muskit.lock.redis.RedisDistributedLockProvider;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Muskit Redis 分布式锁自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.redisson.spring.starter.RedissonAutoConfigurationV4")
@ConditionalOnClass({RedissonClient.class, RedisDistributedLockProvider.class, Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnProperty(prefix = "muskit.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitLockProperties.class)
public class MuskitLockAutoConfiguration {

    /**
     * 创建 Muskit 分布式锁自动配置。
     */
    public MuskitLockAutoConfiguration() {
    }

    /**
     * 创建锁指标记录器；应用没有 MeterRegistry 时使用空实现。
     *
     * @param meterRegistries 应用中的指标注册表
     * @param observationRegistries Muskit 统一观测注册器
     * @return 锁指标记录器
     */
    @Bean
    @ConditionalOnMissingBean
    LockObservation muskitLockObservation(
            ObjectProvider<MeterRegistry> meterRegistries,
            ObjectProvider<MuskitObservationRegistry> observationRegistries) {
        MuskitObservationRegistry observationRegistry = observationRegistries.getIfAvailable();
        if (observationRegistry != null) {
            return new UnifiedLockObservation(observationRegistry);
        }
        MeterRegistry meterRegistry = meterRegistries.orderedStream().findFirst().orElse(null);
        return meterRegistry == null
                ? new NoOpLockObservation()
                : new MicrometerLockObservation(meterRegistry);
    }

    /**
     * 创建 Redis 优先且仅按注解显式允许本地降级的锁提供器。
     *
     * @param redissonClient Redisson 客户端
     * @param properties 锁配置属性
     * @param lockObservation 锁指标记录器
     * @return 锁提供器
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockProvider muskitDistributedLockProvider(
            RedissonClient redissonClient,
            MuskitLockProperties properties,
            LockObservation lockObservation) {
        DistributedLockProvider redisProvider = new RedisDistributedLockProvider(
                redissonClient, properties.getKeyPrefix());
        return new RedisFailureFallbackLockProvider(
                redisProvider, new LocalDistributedLockProvider(), lockObservation);
    }

    /**
     * 创建分布式锁注解切面。
     *
     * @param lockProvider 锁提供器
     * @param lockObservation 锁指标记录器
     * @param beanFactory Spring Bean 工厂
     * @param properties 锁配置属性
     * @return 分布式锁切面
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect muskitDistributedLockAspect(
            DistributedLockProvider lockProvider,
            LockObservation lockObservation,
            BeanFactory beanFactory,
            MuskitLockProperties properties) {
        return new DistributedLockAspect(
                lockProvider, lockObservation, beanFactory, properties.getOrder());
    }
}
