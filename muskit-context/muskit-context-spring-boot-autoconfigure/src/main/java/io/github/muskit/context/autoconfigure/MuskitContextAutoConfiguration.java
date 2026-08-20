package io.github.muskit.context.autoconfigure;

import io.micrometer.context.ContextRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Muskit 业务上下文传播自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnClass(ContextRegistry.class)
@ConditionalOnProperty(prefix = "muskit.context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitContextProperties.class)
public class MuskitContextAutoConfiguration {

    /**
     * 创建 Muskit 业务上下文自动配置。
     */
    public MuskitContextAutoConfiguration() {
    }

    /**
     * 创建 Muskit 线程上下文访问器。
     *
     * @return Muskit 线程上下文访问器
     */
    @Bean
    @ConditionalOnMissingBean
    public MuskitContextThreadLocalAccessor muskitContextThreadLocalAccessor() {
        return new MuskitContextThreadLocalAccessor();
    }

    /**
     * 创建全局上下文访问器注册器。
     *
     * @param accessor Muskit 线程上下文访问器
     * @return 上下文访问器注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public MuskitContextAccessorRegistrar muskitContextAccessorRegistrar(
            MuskitContextThreadLocalAccessor accessor) {
        return new MuskitContextAccessorRegistrar(ContextRegistry.getInstance(), accessor);
    }

    /**
     * 创建基于 Micrometer ContextSnapshot 的 Spring 任务装饰器。
     *
     * @return 上下文传播任务装饰器
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    @ConditionalOnProperty(
            prefix = "muskit.context",
            name = "task-decorator-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public TaskDecorator muskitContextTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
