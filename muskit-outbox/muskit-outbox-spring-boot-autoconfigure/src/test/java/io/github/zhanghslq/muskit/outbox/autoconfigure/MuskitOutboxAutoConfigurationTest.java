package io.github.zhanghslq.muskit.outbox.autoconfigure;

import java.util.List;

import io.github.zhanghslq.muskit.outbox.OutboxDispatchService;
import io.github.zhanghslq.muskit.outbox.OutboxPublisher;
import io.github.zhanghslq.muskit.outbox.OutboxRepository;
import io.github.zhanghslq.muskit.outbox.OutboxService;
import io.github.zhanghslq.muskit.outbox.jdbc.JdbcOutboxRepository;
import io.github.zhanghslq.muskit.outbox.kafka.KafkaOutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Muskit Outbox 自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitOutboxAutoConfigurationTest {

    /**
     * 验证具备 JDBC 和 Kafka 基础设施时会创建完整 Outbox 服务。
     */
    @Test
    void shouldConfigureJdbcKafkaAndServicesByDefault() {
        infrastructureRunner()
                .withPropertyValues("muskit.outbox.scheduler-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxRepository.class);
                    assertThat(context.getBean(OutboxRepository.class)).isInstanceOf(JdbcOutboxRepository.class);
                    assertThat(context).hasSingleBean(OutboxPublisher.class);
                    assertThat(context.getBean(OutboxPublisher.class)).isInstanceOf(KafkaOutboxPublisher.class);
                    assertThat(context).hasSingleBean(OutboxService.class);
                    assertThat(context).hasSingleBean(OutboxDispatchService.class);
                    assertThat(context).doesNotHaveBean(OutboxPollingLifecycle.class);
                });
    }

    /**
     * 验证使用者提供的存储和发布器不会被默认实现覆盖。
     */
    @Test
    void shouldBackOffForUserProvidedComponents() {
        OutboxRepository repository = mockRepository();
        OutboxPublisher publisher = mock(OutboxPublisher.class);

        baseRunner()
                .withBean(OutboxRepository.class, () -> repository)
                .withBean(OutboxPublisher.class, () -> publisher)
                .withPropertyValues("muskit.outbox.scheduler-enabled=false")
                .run(context -> {
                    assertThat(context.getBean(OutboxRepository.class)).isSameAs(repository);
                    assertThat(context.getBean(OutboxPublisher.class)).isSameAs(publisher);
                    assertThat(context).hasSingleBean(OutboxService.class);
                    assertThat(context).hasSingleBean(OutboxDispatchService.class);
                });
    }

    /**
     * 验证后台轮询器默认随 Spring 容器启动并在关闭时停止。
     */
    @Test
    void shouldStartPollingLifecycleByDefault() {
        OutboxPollingLifecycle[] observed = new OutboxPollingLifecycle[1];

        baseRunner()
                .withBean(OutboxRepository.class, this::mockRepository)
                .withBean(OutboxPublisher.class, () -> mock(OutboxPublisher.class))
                .withPropertyValues("muskit.outbox.poll-interval=1h")
                .run(context -> {
                    observed[0] = context.getBean(OutboxPollingLifecycle.class);
                    assertThat(observed[0].isRunning()).isTrue();
                });

        assertThat(observed[0].isRunning()).isFalse();
    }

    /**
     * 验证总开关关闭后不创建任何 Outbox 组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        infrastructureRunner()
                .withPropertyValues("muskit.outbox.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OutboxRepository.class);
                    assertThat(context).doesNotHaveBean(OutboxPublisher.class);
                    assertThat(context).doesNotHaveBean(OutboxService.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatchService.class);
                    assertThat(context).doesNotHaveBean(OutboxPollingLifecycle.class);
                });
    }

    /**
     * 创建只加载 Outbox 自动配置的上下文运行器。
     *
     * @return 上下文运行器
     */
    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                MuskitOutboxJdbcAutoConfiguration.class,
                MuskitOutboxKafkaAutoConfiguration.class,
                MuskitOutboxAutoConfiguration.class));
    }

    /**
     * 创建带模拟 JDBC 和 Kafka 基础设施的上下文运行器。
     *
     * @return 上下文运行器
     */
    private ApplicationContextRunner infrastructureRunner() {
        return baseRunner()
                .withBean(JdbcOperations.class, () -> mock(JdbcOperations.class))
                .withBean(KafkaTemplate.class, this::mockKafkaTemplate);
    }

    /**
     * 创建返回空批次的模拟 Outbox 存储。
     *
     * @return 模拟存储
     */
    private OutboxRepository mockRepository() {
        OutboxRepository repository = mock(OutboxRepository.class);
        when(repository.claimBatch(any(String.class), anyInt(), any())).thenReturn(List.of());
        return repository;
    }

    /**
     * 创建模拟 Kafka 发送模板。
     *
     * @return 模拟 Kafka 发送模板
     */
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, byte[]> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}
