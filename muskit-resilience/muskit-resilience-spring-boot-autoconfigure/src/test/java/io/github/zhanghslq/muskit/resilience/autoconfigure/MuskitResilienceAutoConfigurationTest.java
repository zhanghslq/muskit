package io.github.zhanghslq.muskit.resilience.autoconfigure;

import io.github.zhanghslq.muskit.resilience.autoconfigure.ratelimit.MuskitResilienceRedisAutoConfiguration;
import io.github.zhanghslq.muskit.resilience.autoconfigure.ratelimit.RateLimitGuardAspect;
import io.github.zhanghslq.muskit.resilience.autoconfigure.retry.RetryGuardAspect;
import io.github.zhanghslq.muskit.resilience.ratelimit.LocalTokenBucketRateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitDecision;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRejectedException;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import io.github.zhanghslq.muskit.resilience.ratelimit.redis.RedisTokenBucketRateLimiter;
import io.github.zhanghslq.muskit.resilience.retry.RetryExecutor;
import io.github.zhanghslq.muskit.resilience.retry.RetryGuard;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicyResolver;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Muskit 韧性自动配置和限流切面测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MuskitResilienceRedisAutoConfiguration.class,
                    MuskitResilienceAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "muskit.resilience.rate-limit-policies.tenant-api.capacity=1",
                    "muskit.resilience.rate-limit-policies.tenant-api.refill-tokens=1",
                    "muskit.resilience.rate-limit-policies.tenant-api.refill-period=1h",
                    "muskit.resilience.rate-limit-policies.tenant-api.scope=key",
                    "muskit.resilience.retry-policies.remote-call.max-attempts=3",
                    "muskit.resilience.retry-policies.remote-call.initial-delay=0ms",
                    "muskit.resilience.retry-policies.remote-call.multiplier=2",
                    "muskit.resilience.retry-policies.remote-call.max-delay=0ms",
                    "muskit.resilience.retry-policies.remote-call.jitter=0");

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
     * 验证显式选择 Redis 时创建分布式令牌桶 Provider。
     */
    @Test
    void shouldConfigureRedisRateLimiterExplicitly() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(mock(RScript.class));

        contextRunner.withBean(RedissonClient.class, () -> client)
                .withPropertyValues("muskit.resilience.rate-limit-provider=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context.getBean(RateLimiter.class))
                            .isInstanceOf(RedisTokenBucketRateLimiter.class);
                });
    }

    /**
     * 验证选择 Redis 但缺少客户端时不会静默降级为本地限流。
     */
    @Test
    void shouldFailWhenRedisRateLimiterHasNoClient() {
        contextRunner.withPropertyValues("muskit.resilience.rate-limit-provider=redis")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证注解重试使用配置策略并在第三次调用成功。
     */
    @Test
    void shouldApplyConfiguredRetryPolicy() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RetryExecutor.class);
            assertThat(context).hasSingleBean(RetryPolicyResolver.class);
            assertThat(context).hasSingleBean(RetryGuardAspect.class);

            RetryingService service = context.getBean(RetryingService.class);
            assertThat(service.call()).isEqualTo("ok");
            assertThat(service.calls()).isEqualTo(3);
        });
    }

    /**
     * 验证显式禁用重试后不创建重试组件。
     */
    @Test
    void shouldDisableRetryExplicitly() {
        contextRunner.withPropertyValues("muskit.resilience.retry-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RetryExecutor.class);
                    assertThat(context).doesNotHaveBean(RetryGuardAspect.class);
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

        /**
         * 创建带重试注解的测试服务。
         *
         * @return 重试测试服务
         */
        @Bean
        RetryingService retryingService() {
            return new RetryingService();
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

    /**
     * 前两次失败、第三次成功的重试测试服务。
     *
     * @author zhs
     * @since 2026-08-20
     */
    static class RetryingService {

        private int calls;

        /**
         * 创建重试测试服务。
         */
        RetryingService() {
        }

        /**
         * 在第三次调用返回成功结果。
         *
         * @return 成功结果
         */
        @RetryGuard(policy = "remote-call")
        String call() {
            calls++;
            if (calls < 3) {
                throw new IllegalStateException("temporary");
            }
            return "ok";
        }

        /**
         * 返回目标对象累计调用次数。
         *
         * @return 调用次数
         */
        int calls() {
            return calls;
        }
    }
}
