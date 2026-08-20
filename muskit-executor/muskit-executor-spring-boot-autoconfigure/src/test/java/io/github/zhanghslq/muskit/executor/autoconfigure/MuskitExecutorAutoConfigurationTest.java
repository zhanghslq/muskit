package io.github.zhanghslq.muskit.executor.autoconfigure;

import io.github.zhanghslq.muskit.executor.ExecutorType;
import io.github.zhanghslq.muskit.executor.ManagedExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受管执行器自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitExecutorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MuskitExecutorAutoConfiguration.class);

    /**
     * 验证默认创建名为 default 的虚拟线程执行器。
     */
    @Test
    void shouldConfigureDefaultVirtualExecutor() {
        contextRunner.run(context -> {
            ManagedExecutorRegistry registry = context.getBean(ManagedExecutorRegistry.class);
            assertThat(registry.names()).containsExactly("default");
            assertThat(registry.defaultExecutor().snapshot().type()).isEqualTo(ExecutorType.VIRTUAL);
        });
    }

    /**
     * 验证业务可以配置平台线程执行器的并发和等待容量。
     */
    @Test
    void shouldBindNamedPlatformExecutor() {
        contextRunner.withPropertyValues(
                        "muskit.executor.executors.default.type=platform",
                        "muskit.executor.executors.default.max-concurrency=3",
                        "muskit.executor.executors.default.queue-capacity=4")
                .run(context -> {
                    var snapshot = context.getBean(ManagedExecutorRegistry.class).defaultExecutor().snapshot();
                    assertThat(snapshot.type()).isEqualTo(ExecutorType.PLATFORM);
                    assertThat(snapshot.availableCapacity()).isEqualTo(7);
                });
    }

    /**
     * 验证可以显式关闭执行器自动配置。
     */
    @Test
    void shouldDisableExecutor() {
        contextRunner.withPropertyValues("muskit.executor.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ManagedExecutorRegistry.class));
    }
}
