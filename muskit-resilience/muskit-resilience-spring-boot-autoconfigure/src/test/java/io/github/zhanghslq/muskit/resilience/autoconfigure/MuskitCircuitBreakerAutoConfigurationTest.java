package io.github.zhanghslq.muskit.resilience.autoconfigure;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerGuard;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerOpenException;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.resilience4j.Resilience4jCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 熔断 Provider 自动配置和注解切面测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitCircuitBreakerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MuskitCircuitBreakerResilience4jAutoConfiguration.class,
                    MuskitCircuitBreakerAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "muskit.resilience.circuit-breaker-policies.inventory.failure-rate-threshold=50",
                    "muskit.resilience.circuit-breaker-policies.inventory.slow-call-rate-threshold=100",
                    "muskit.resilience.circuit-breaker-policies.inventory.slow-call-duration-threshold=1h",
                    "muskit.resilience.circuit-breaker-policies.inventory.minimum-number-of-calls=2",
                    "muskit.resilience.circuit-breaker-policies.inventory.sliding-window-size=2",
                    "muskit.resilience.circuit-breaker-policies.inventory.permitted-calls-in-half-open=1",
                    "muskit.resilience.circuit-breaker-policies.inventory.wait-duration-in-open-state=30s",
                    "muskit.resilience.circuit-breaker-policies.async.minimum-number-of-calls=1",
                    "muskit.resilience.circuit-breaker-policies.async.sliding-window-size=1");

    /**
     * 验证同步失败达到阈值后切面拒绝业务调用。
     */
    @Test
    void shouldOpenAndRejectSynchronousCall() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CircuitBreaker.class);
            assertThat(context.getBean(CircuitBreaker.class)).isInstanceOf(Resilience4jCircuitBreaker.class);
            assertThat(context).hasSingleBean(CircuitBreakerGuardAspect.class);
            FailingService service = context.getBean(FailingService.class);

            assertThatThrownBy(service::call).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(service::call).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(service::call).isInstanceOf(CircuitBreakerOpenException.class);
            assertThat(service.calls()).isEqualTo(2);
        });
    }

    /**
     * 验证 CompletionStage 真实失败后记录熔断结果。
     */
    @Test
    void shouldRecordAsynchronousCompletionFailure() {
        contextRunner.run(context -> {
            FailingService service = context.getBean(FailingService.class);

            assertThatThrownBy(() -> service.asyncCall().toCompletableFuture().join())
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(service::asyncCall).isInstanceOf(CircuitBreakerOpenException.class);
        });
    }

    /**
     * 验证显式禁用后不创建 Resilience4j Provider 和熔断切面。
     */
    @Test
    void shouldDisableCircuitBreakerExplicitly() {
        contextRunner.withPropertyValues("muskit.resilience.circuit-breaker-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CircuitBreaker.class);
                    assertThat(context).doesNotHaveBean(CircuitBreakerGuardAspect.class);
                });
    }

    /**
     * 熔断切面测试配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        /**
         * 创建熔断测试服务。
         *
         * @return 熔断测试服务
         */
        @Bean
        FailingService failingService() {
            return new FailingService();
        }
    }

    /**
     * 始终失败的同步和异步熔断测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class FailingService {

        private int calls;

        /**
         * 创建熔断测试服务。
         */
        FailingService() {
        }

        /**
         * 执行一次同步失败调用。
         *
         * @return 永远不会返回
         */
        @CircuitBreakerGuard(policy = "inventory")
        String call() {
            calls++;
            throw new IllegalStateException("inventory unavailable");
        }

        /**
         * 执行一次异步失败调用。
         *
         * @return 异步失败结果
         */
        @CircuitBreakerGuard(policy = "async")
        CompletionStage<String> asyncCall() {
            return CompletableFuture.failedFuture(new IllegalStateException("async unavailable"));
        }

        /**
         * 返回同步目标累计调用次数。
         *
         * @return 调用次数
         */
        int calls() {
            return calls;
        }
    }
}
