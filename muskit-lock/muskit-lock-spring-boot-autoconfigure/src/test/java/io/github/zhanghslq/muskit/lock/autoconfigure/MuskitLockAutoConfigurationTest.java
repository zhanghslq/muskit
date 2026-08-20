package io.github.zhanghslq.muskit.lock.autoconfigure;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.zhanghslq.muskit.lock.DistributedLock;
import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockInterruptedException;
import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.lock.DistributedLockRejectedException;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.DistributedLockUnavailableException;
import io.github.zhanghslq.muskit.lock.LocalDistributedLockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * MuskitLockAutoConfiguration 自动配置、切面和降级提示测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ExtendWith(OutputCaptureExtension.class)
class MuskitLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitLockAutoConfiguration.class));

    /**
     * 验证存在 RedissonClient 时创建 Redis 优先锁提供器和切面。
     */
    @Test
    void shouldConfigureDefaultBeans() {
        contextRunner.withUserConfiguration(RedissonConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(DistributedLockProvider.class);
            assertThat(context.getBean(DistributedLockProvider.class))
                    .isInstanceOf(RedisFailureFallbackLockProvider.class);
            assertThat(context).hasSingleBean(DistributedLockAspect.class);
            assertThat(context).hasSingleBean(LockObservation.class);
            assertThat(context.getBean(LockObservation.class)).isInstanceOf(NoOpLockObservation.class);
        });
    }

    /**
     * 验证启用锁但缺少 RedissonClient 时启动失败，不会自动创建本地实现。
     */
    @Test
    void shouldFailWhenRedissonClientIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("RedissonClient");
        });
    }

    /**
     * 验证关闭总开关后不需要 RedissonClient，也不会创建锁组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner.withPropertyValues("muskit.lock.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DistributedLockProvider.class);
            assertThat(context).doesNotHaveBean(DistributedLockAspect.class);
        });
    }

    /**
     * 验证 Provider 等待被中断时切面恢复线程中断标记并转换公共异常。
     */
    @Test
    void shouldRestoreInterruptFlag() {
        contextRunner.withUserConfiguration(InterruptingTestConfiguration.class).run(context -> {
            SyncGuardedService service = context.getBean(SyncGuardedService.class);
            try {
                assertThatThrownBy(service::execute)
                        .isInstanceOf(DistributedLockInterruptedException.class)
                        .hasMessageContaining("interrupt-test");
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
        });
    }

    /**
     * 验证注解属性和 SpEL 键传入 Provider，并在异步结果完成前持续持锁。
     */
    @Test
    void shouldApplyAnnotationAndHoldLockUntilAsyncCompletion() {
        contextRunner.withUserConfiguration(AspectTestConfiguration.class).run(context -> {
            GuardedAsyncService service = context.getBean(GuardedAsyncService.class);
            RecordingLockProvider provider = context.getBean(RecordingLockProvider.class);

            CompletableFuture<String> first = service.execute("42").toCompletableFuture();
            assertThat(provider.lastRequest()).satisfies(request -> {
                assertThat(request.name()).isEqualTo("order-submit");
                assertThat(request.key()).isEqualTo("42");
                assertThat(request.waitTime()).isEqualTo(Duration.ofMillis(500));
                assertThat(request.leaseTime()).isEqualTo(Duration.ofSeconds(30));
                assertThat(request.fair()).isTrue();
                assertThat(request.localFallback()).isTrue();
            });
            assertThatThrownBy(() -> service.execute("42"))
                    .isInstanceOf(DistributedLockRejectedException.class)
                    .hasMessageContaining("order-submit")
                    .hasMessageNotContaining("42");

            service.complete("done");
            assertThat(first).isCompletedWithValue("done");
            CompletableFuture<String> second = service.execute("42").toCompletableFuture();
            service.complete("done-again");
            assertThat(second).isCompletedWithValue("done-again");
        });
    }

    /**
     * 验证存在 MeterRegistry 时记录低基数的锁获取结果和耗时。
     */
    @Test
    void shouldRecordLowCardinalityMetrics() {
        contextRunner
                .withUserConfiguration(AspectTestConfiguration.class, MeterRegistryConfiguration.class)
                .run(context -> {
                    GuardedAsyncService service = context.getBean(GuardedAsyncService.class);
                    SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
                    CompletableFuture<String> first = service.execute("metrics-sensitive-key").toCompletableFuture();

                    assertThatThrownBy(() -> service.execute("metrics-sensitive-key"))
                            .isInstanceOf(DistributedLockRejectedException.class);
                    service.complete("done");
                    assertThat(first).isCompletedWithValue("done");

                    assertThat(registry.find(MicrometerLockObservation.ACQUIRE_METRIC_NAME)
                            .tags("lock", "order-submit", "provider", "redis-first", "outcome", "acquired")
                            .timer())
                            .isNotNull()
                            .extracting(timer -> timer.count())
                            .isEqualTo(1L);
                    assertThat(registry.find(MicrometerLockObservation.ACQUIRE_METRIC_NAME)
                            .tags("lock", "order-submit", "provider", "redis-first", "outcome", "rejected")
                            .timer())
                            .isNotNull()
                            .extracting(timer -> timer.count())
                            .isEqualTo(1L);
                    assertThat(registry.getMeters()).allSatisfy(meter ->
                            assertThat(meter.getId().toString()).doesNotContain("metrics-sensitive-key"));
                });
    }

    /**
     * 验证业务显式允许时才降级，并输出不含业务锁键的明确警告。
     *
     * @param output 测试期间捕获的日志输出
     * @throws Exception 本地锁等待被中断
     */
    @Test
    void shouldWarnWhenRedisFailureFallsBackToLocal(CapturedOutput output) throws Exception {
        DistributedLockProvider unavailable = request -> {
            throw new DistributedLockUnavailableException(
                    request.name(), new IllegalStateException("redis failure for sensitive-key"));
        };
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedisFailureFallbackLockProvider provider = new RedisFailureFallbackLockProvider(
                unavailable,
                new LocalDistributedLockProvider(),
                new MicrometerLockObservation(meterRegistry));
        DistributedLockRequest fallbackRequest = new DistributedLockRequest(
                "order-submit", "sensitive-key", Duration.ZERO, Duration.ZERO, false, true);
        DistributedLockRequest failFastRequest = new DistributedLockRequest(
                "order-submit", "sensitive-key", Duration.ZERO, Duration.ZERO, false, false);

        DistributedLockHandle handle = provider.tryAcquire(fallbackRequest).orElseThrow();
        handle.close();
        assertThat(output)
                .contains("已降级为仅当前 JVM 生效的本地锁")
                .contains("跨实例互斥不再保证")
                .doesNotContain("sensitive-key");
        assertThat(meterRegistry.find(MicrometerLockObservation.FALLBACK_METRIC_NAME)
                .tags("lock", "order-submit", "provider", "local-fallback", "outcome", "activated")
                .counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(1.0d);
        assertThatThrownBy(() -> provider.tryAcquire(failFastRequest))
                .isInstanceOf(DistributedLockUnavailableException.class);
    }

    /**
     * 提供自动配置所需的 RedissonClient。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class RedissonConfiguration {

        /**
         * 创建无需连接 Redis 的模拟客户端。
         *
         * @return 模拟 RedissonClient
         */
        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }
    }

    /**
     * 提供切面测试服务和可记录锁请求的 Provider。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AspectTestConfiguration {

        /**
         * 创建可记录锁请求的 Provider。
         *
         * @return 测试锁 Provider
         */
        @Bean
        RecordingLockProvider recordingLockProvider() {
            return new RecordingLockProvider();
        }

        /**
         * 创建受分布式锁保护的异步服务。
         *
         * @return 测试异步服务
         */
        @Bean
        GuardedAsyncService guardedAsyncService() {
            return new GuardedAsyncService();
        }
    }

    /**
     * 提供锁可观测性测试使用的内存指标注册表。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        /**
         * 创建内存指标注册表。
         *
         * @return 内存指标注册表
         */
        @Bean
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /**
     * 提供始终中断等待的锁 Provider 和同步测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class InterruptingTestConfiguration {

        /**
         * 创建始终抛出中断异常的锁 Provider。
         *
         * @return 中断锁 Provider
         */
        @Bean
        DistributedLockProvider interruptingLockProvider() {
            return request -> {
                throw new InterruptedException("interrupted");
            };
        }

        /**
         * 创建同步锁测试服务。
         *
         * @return 同步测试服务
         */
        @Bean
        SyncGuardedService syncGuardedService() {
            return new SyncGuardedService();
        }
    }

    /**
     * 记录最近锁请求并模拟单许可互斥的 Provider。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static final class RecordingLockProvider implements DistributedLockProvider {

        private final AtomicBoolean held = new AtomicBoolean();
        private volatile DistributedLockRequest lastRequest;

        /**
         * 创建测试锁 Provider。
         */
        RecordingLockProvider() {
        }

        /**
         * 记录锁请求并模拟获取互斥许可。
         *
         * @param request 锁请求
         * @return 获取成功时返回释放句柄，否则返回空
         */
        @Override
        public Optional<DistributedLockHandle> tryAcquire(DistributedLockRequest request) {
            lastRequest = request;
            if (!held.compareAndSet(false, true)) {
                return Optional.empty();
            }
            AtomicBoolean closed = new AtomicBoolean();
            return Optional.of(() -> {
                if (closed.compareAndSet(false, true)) {
                    held.set(false);
                }
            });
        }

        /**
         * 返回最近记录的锁请求。
         *
         * @return 最近锁请求
         */
        DistributedLockRequest lastRequest() {
            return lastRequest;
        }
    }

    /**
     * 返回未完成异步结果的测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class GuardedAsyncService {

        private volatile CompletableFuture<String> future;

        /**
         * 创建异步锁测试服务。
         */
        GuardedAsyncService() {
        }

        /**
         * 创建受 Redis 分布式锁保护的异步任务。
         *
         * @param orderId 订单标识
         * @return 未完成的异步结果
         */
        @DistributedLock(
                name = "order-submit",
                key = "#orderId",
                waitTime = 500,
                leaseTime = 30_000,
                timeUnit = TimeUnit.MILLISECONDS,
                fair = true,
                localFallback = true)
        public CompletionStage<String> execute(String orderId) {
            future = new CompletableFuture<>();
            return future;
        }

        /**
         * 完成最近创建的异步任务。
         *
         * @param value 完成值
         */
        void complete(String value) {
            future.complete(value);
        }
    }

    /**
     * 用于验证锁等待中断语义的同步服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class SyncGuardedService {

        /**
         * 创建同步锁测试服务。
         */
        SyncGuardedService() {
        }

        /**
         * 执行受锁保护的同步调用。
         *
         * @return 固定测试结果
        */
        @DistributedLock(name = "interrupt-test")
        public String execute() {
            return "done";
        }
    }
}
