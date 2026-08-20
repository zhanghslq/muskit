package io.github.zhanghslq.muskit.executor.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.github.zhanghslq.muskit.executor.ManagedExecutorConfig;
import io.github.zhanghslq.muskit.executor.ManagedExecutorRegistry;
import io.github.zhanghslq.muskit.executor.ManagedTaskExecutor;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Muskit 平台线程和虚拟线程受管执行器自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "muskit.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitExecutorProperties.class)
public class MuskitExecutorAutoConfiguration {

    /**
     * 创建受管执行器自动配置。
     */
    public MuskitExecutorAutoConfiguration() {
    }

    /**
     * 根据类型安全配置创建执行器注册表。
     *
     * @param properties 执行器配置
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return 受管执行器注册表
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ManagedExecutorRegistry muskitManagedExecutorRegistry(
            MuskitExecutorProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        MuskitObservationRegistry observationRegistry = observationRegistryProvider
                .getIfAvailable(MuskitObservationRegistry::noop);
        List<ManagedTaskExecutor> executors = new ArrayList<>();
        properties.getExecutors().forEach((name, spec) -> executors.add(new ManagedTaskExecutor(
                new ManagedExecutorConfig(
                        name,
                        spec.getType(),
                        spec.getMaxConcurrency(),
                        spec.getQueueCapacity(),
                        spec.getShutdownTimeout()),
                observationRegistry)));
        return new ManagedExecutorRegistry(executors);
    }
}
