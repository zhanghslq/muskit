package io.github.muskit.concurrency.autoconfigure;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.muskit.concurrency.ConcurrencyGuard;
import io.github.muskit.concurrency.ConcurrencyLimiter;
import io.github.muskit.concurrency.ConcurrencyPolicyResolver;
import io.github.muskit.concurrency.ConcurrencyRejectedException;
import io.github.muskit.concurrency.LocalConcurrencyLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MuskitConcurrencyAutoConfiguration 自动配置和切面集成测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitConcurrencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitConcurrencyAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "muskit.concurrency.policies.tenant-export.max-concurrency=1",
                    "muskit.concurrency.policies.tenant-export.max-wait=0ms",
                    "muskit.concurrency.policies.tenant-export.scope=key");

    /**
     * 验证默认创建本地并发控制器和配置策略解析器。
     */
    @Test
    void shouldConfigureDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ConcurrencyLimiter.class);
            assertThat(context.getBean(ConcurrencyLimiter.class)).isInstanceOf(LocalConcurrencyLimiter.class);
            assertThat(context).hasSingleBean(ConcurrencyPolicyResolver.class);
            assertThat(context).hasSingleBean(ConcurrencyGuardAspect.class);
        });
    }

    /**
     * 验证异步任务完成前保持按业务键获取的并发额度。
     */
    @Test
    void shouldHoldPermitUntilCompletionStageFinishes() {
        contextRunner.run(context -> {
            GuardedAsyncService service = context.getBean(GuardedAsyncService.class);
            CompletableFuture<String> tenantA = service.execute("A").toCompletableFuture();
            CompletableFuture<String> tenantB = service.execute("B").toCompletableFuture();

            assertThatThrownBy(() -> service.execute("A"))
                    .isInstanceOf(ConcurrencyRejectedException.class)
                    .hasMessageContaining("tenant-export");

            service.complete("A", "done");
            CompletableFuture<String> tenantASecond = service.execute("A").toCompletableFuture();

            service.complete("A", "done-again");
            service.complete("B", "done");
            assertThat(tenantASecond).isCompletedWithValue("done-again");
            assertThat(tenantA).isCompletedWithValue("done");
            assertThat(tenantB).isCompletedWithValue("done");
        });
    }

    /**
     * 验证关闭总开关后不会创建并发治理组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner
                .withPropertyValues("muskit.concurrency.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ConcurrencyGuardAspect.class));
    }

    /**
     * 并发控制切面测试配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        /**
         * 创建受并发策略保护的异步服务。
         *
         * @return 测试异步服务
         */
        @Bean
        GuardedAsyncService guardedAsyncService() {
            return new GuardedAsyncService();
        }
    }

    /**
     * 返回未完成异步结果的测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class GuardedAsyncService {

        private final ConcurrentMap<String, CompletableFuture<String>> futures = new ConcurrentHashMap<>();

        /**
         * 创建受租户键限制的异步任务。
         *
         * @param tenantId 租户标识
         * @return 未完成的异步结果
         */
        @ConcurrencyGuard(policy = "tenant-export", key = "#tenantId")
        public CompletionStage<String> execute(String tenantId) {
            CompletableFuture<String> future = new CompletableFuture<>();
            futures.put(tenantId, future);
            return future;
        }

        /**
         * 完成指定租户最近创建的异步任务。
         *
         * @param tenantId 租户标识
         * @param value 完成值
         */
        public void complete(String tenantId, String value) {
            futures.get(tenantId).complete(value);
        }
    }
}
