package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.resilience.ratelimit.LocalTokenBucketRateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitDecision;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRejectedException;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Muskit 韧性自动配置和限流切面测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuskitResilienceAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "muskit.resilience.rate-limit-policies.tenant-api.capacity=1",
                    "muskit.resilience.rate-limit-policies.tenant-api.refill-tokens=1",
                    "muskit.resilience.rate-limit-policies.tenant-api.refill-period=1h",
                    "muskit.resilience.rate-limit-policies.tenant-api.scope=key");

    /**
     * 验证默认 Bean 和按业务键隔离的注解限流行为。
     */
    @Test
    void shouldConfigureAndApplyLocalRateLimit() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RateLimiter.class);
            assertThat(context.getBean(RateLimiter.class)).isInstanceOf(LocalTokenBucketRateLimiter.class);
            assertThat(context).hasSingleBean(RateLimitPolicyResolver.class);
            assertThat(context).hasSingleBean(RateLimitGuardAspect.class);
            RateLimitedService service = context.getBean(RateLimitedService.class);

            assertThat(service.call("confidential-42")).isEqualTo("confidential-42");
            assertThatThrownBy(() -> service.call("confidential-42"))
                    .isInstanceOf(RateLimitRejectedException.class)
                    .hasMessageContaining("tenant-api")
                    .hasMessageNotContaining("confidential-42");
            assertThat(service.call("tenant-b")).isEqualTo("tenant-b");
        });
    }

    /**
     * 验证用户提供的限流 Provider 会替换默认实现。
     */
    @Test
    void shouldBackOffForUserRateLimiter() {
        RateLimiter custom = request -> RateLimitDecision.permitted();

        contextRunner.withBean(RateLimiter.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(RateLimiter.class);
            assertThat(context.getBean(RateLimiter.class)).isSameAs(custom);
            RateLimitedService service = context.getBean(RateLimitedService.class);
            assertThat(service.call("tenant-a")).isEqualTo("tenant-a");
            assertThat(service.call("tenant-a")).isEqualTo("tenant-a");
        });
    }

    /**
     * 验证显式禁用后不创建限流组件。
     */
    @Test
    void shouldDisableRateLimitExplicitly() {
        contextRunner.withPropertyValues("muskit.resilience.rate-limit-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimiter.class);
                    assertThat(context).doesNotHaveBean(RateLimitGuardAspect.class);
                    RateLimitedService service = context.getBean(RateLimitedService.class);
                    assertThat(service.call("tenant-a")).isEqualTo("tenant-a");
                    assertThat(service.call("tenant-a")).isEqualTo("tenant-a");
                });
    }

    /**
     * 限流切面测试配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        /**
         * 创建测试配置。
         */
        TestConfiguration() {
        }

        /**
         * 创建带限流注解的测试服务。
         *
         * @return 测试服务
         */
        @Bean
        RateLimitedService rateLimitedService() {
            return new RateLimitedService();
        }
    }

    /**
     * 带按租户限流注解的测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class RateLimitedService {

        /**
         * 创建测试服务。
         */
        RateLimitedService() {
        }

        /**
         * 返回租户标识。
         *
         * @param tenantId 租户标识
         * @return 租户标识
         */
        @RateLimitGuard(policy = "tenant-api", key = "#tenantId")
        String call(String tenantId) {
            return tenantId;
        }
    }
}
