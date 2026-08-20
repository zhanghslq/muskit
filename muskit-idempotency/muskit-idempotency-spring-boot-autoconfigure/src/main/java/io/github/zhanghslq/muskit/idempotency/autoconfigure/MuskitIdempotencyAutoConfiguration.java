package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
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
     * @return 幂等切面
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect muskitIdempotentAspect(
            IdempotencyStore store,
            BeanFactory beanFactory,
            MuskitIdempotencyProperties properties) {
        return new IdempotentAspect(store, beanFactory, properties.getOrder());
    }
}
