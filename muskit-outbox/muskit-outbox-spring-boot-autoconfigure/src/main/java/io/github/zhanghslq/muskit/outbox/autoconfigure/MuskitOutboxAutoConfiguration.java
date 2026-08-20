package io.github.zhanghslq.muskit.outbox.autoconfigure;

import io.github.zhanghslq.muskit.outbox.OutboxDispatchService;
import io.github.zhanghslq.muskit.outbox.OutboxDeadLetterSink;
import io.github.zhanghslq.muskit.outbox.OutboxPublisher;
import io.github.zhanghslq.muskit.outbox.OutboxRepository;
import io.github.zhanghslq.muskit.outbox.OutboxRetryPolicy;
import io.github.zhanghslq.muskit.outbox.OutboxService;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 与具体数据库和消息系统无关的 Outbox 服务自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = {
        MuskitOutboxJdbcAutoConfiguration.class,
        MuskitOutboxKafkaAutoConfiguration.class
})
@ConditionalOnBean({OutboxRepository.class, OutboxPublisher.class})
@ConditionalOnProperty(prefix = "muskit.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitOutboxProperties.class)
public class MuskitOutboxAutoConfiguration {

    /**
     * 创建 Outbox 服务自动配置。
     */
    public MuskitOutboxAutoConfiguration() {
    }

    /**
     * 创建业务事务内写入 Outbox 事件的服务。
     *
     * @param repository Outbox 存储
     * @return Outbox 服务
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxService muskitOutboxService(OutboxRepository repository) {
        return new OutboxService(repository);
    }

    /**
     * 创建竞争租约并批量发布事件的服务。
     *
     * @param repository Outbox 存储
     * @param publisher Outbox 发布器
     * @param properties Outbox 配置属性
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @param deadLetterSinkProvider 外部死信通知 Provider
     * @return Outbox 批量发布服务
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxDispatchService muskitOutboxDispatchService(
            OutboxRepository repository,
            OutboxPublisher publisher,
            MuskitOutboxProperties properties,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider,
            ObjectProvider<OutboxDeadLetterSink> deadLetterSinkProvider) {
        return new OutboxDispatchService(
                repository,
                publisher,
                properties.getBatchSize(),
                properties.getLeaseTime(),
                new OutboxRetryPolicy(
                        properties.getMaxAttempts(),
                        properties.getRetryDelay(),
                        properties.getRetryMultiplier(),
                        properties.getMaxRetryDelay()),
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop),
                deadLetterSinkProvider.getIfAvailable(OutboxDeadLetterSink::noop));
    }

    /**
     * 创建随容器生命周期管理的后台轮询器。
     *
     * @param dispatchService Outbox 批量发布服务
     * @param repository Outbox 存储
     * @param properties Outbox 配置属性
     * @return Outbox 后台轮询器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.outbox",
            name = "scheduler-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public OutboxPollingLifecycle muskitOutboxPollingLifecycle(
            OutboxDispatchService dispatchService,
            OutboxRepository repository,
            MuskitOutboxProperties properties) {
        return new OutboxPollingLifecycle(
                dispatchService,
                repository,
                properties.getPollInterval(),
                properties.getPublishedRetention());
    }
}
