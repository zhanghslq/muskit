package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import io.github.zhanghslq.muskit.idempotency.autoconfigure.aspect.IdempotentAspect;
import io.github.zhanghslq.muskit.idempotency.autoconfigure.jdbc.MuskitIdempotencyJdbcAutoConfiguration;
import io.github.zhanghslq.muskit.idempotency.autoconfigure.redis.MuskitIdempotencyRedisAutoConfiguration;
import io.github.zhanghslq.muskit.idempotency.service.IdempotencyTemplate;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Muskit 幂等切面自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = {
        MuskitIdempotencyRedisAutoConfiguration.class,
        MuskitIdempotencyJdbcAutoConfiguration.class
})
@ConditionalOnClass({Aspect.class, ProceedingJoinPoint.class})
@ConditionalOnProperty(prefix = "muskit.idempotency", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitIdempotencyProperties.class)
public class MuskitIdempotencyAutoConfiguration {

    /**
     * 创建幂等切面自动配置。
     */
    public MuskitIdempotencyAutoConfiguration() {
    }

    /**
     * 创建幂等注解切面；缺少所选 IdempotencyStore 时应用启动失败。
     *
     * @param store 幂等状态存储
     * @param beanFactory Spring Bean 工厂
     * @param properties 幂等配置属性
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @param renewalSchedulerProvider lease 续期调度器 Provider
     * @return 幂等切面
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect muskitIdempotentAspect(
            IdempotencyStore store,
            BeanFactory beanFactory,
            MuskitIdempotencyProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider,
            @Qualifier("muskitIdempotencyLeaseScheduler")
            ObjectProvider<ScheduledExecutorService> renewalSchedulerProvider) {
        return new IdempotentAspect(
                store,
                beanFactory,
                properties.getOrder(),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop),
                renewalSchedulerProvider.getIfAvailable(),
                properties.isLeaseRenewalEnabled());
    }

    /**
     * 创建程序化业务 ID 幂等模板。
     *
     * @param store 幂等状态存储
     * @return 幂等模板
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyTemplate muskitIdempotencyTemplate(IdempotencyStore store) {
        return new IdempotencyTemplate(store);
    }

    /**
     * 创建单线程守护型幂等 lease 续期执行器。
     *
     * @return lease 续期执行器
     */
    @Bean(name = "muskitIdempotencyLeaseScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "muskitIdempotencyLeaseScheduler")
    @ConditionalOnProperty(
            prefix = "muskit.idempotency",
            name = "lease-renewal-enabled",
            havingValue = "true")
    public ScheduledExecutorService muskitIdempotencyLeaseScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform()
                .daemon()
                .name("muskit-idempotency-renewal")
                .unstarted(task));
    }
}
