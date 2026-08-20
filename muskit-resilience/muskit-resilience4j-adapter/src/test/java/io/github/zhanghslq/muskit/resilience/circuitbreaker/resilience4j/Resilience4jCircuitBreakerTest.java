package io.github.zhanghslq.muskit.resilience.circuitbreaker.resilience4j;

import java.time.Duration;
import java.util.Set;

import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerOpenException;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPermit;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicy;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resilience4j 熔断 Provider 测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class Resilience4jCircuitBreakerTest {

    /**
     * 验证达到失败率阈值后开启并拒绝后续调用。
     */
    @Test
    void shouldOpenAfterFailureRateThreshold() {
        Resilience4jCircuitBreaker breaker = new Resilience4jCircuitBreaker();
        CircuitBreakerPolicy policy = policy(Set.of(Exception.class), Set.of(), Duration.ofSeconds(1));

        fail(breaker, policy, new IllegalStateException("first"), Duration.ofMillis(1));
        fail(breaker, policy, new IllegalStateException("second"), Duration.ofMillis(1));

        assertThat(breaker.state(policy)).isEqualTo(CircuitBreakerState.OPEN);
        assertThatThrownBy(() -> breaker.acquire(policy))
                .isInstanceOf(CircuitBreakerOpenException.class)
                .hasMessageContaining("inventory-service");
    }

    /**
     * 验证配置为忽略的异常不会计入失败率。
     */
    @Test
    void shouldIgnoreConfiguredFailureType() {
        Resilience4jCircuitBreaker breaker = new Resilience4jCircuitBreaker();
        CircuitBreakerPolicy policy = policy(
                Set.of(Exception.class), Set.of(IllegalArgumentException.class), Duration.ofSeconds(1));

        fail(breaker, policy, new IllegalArgumentException("business validation"), Duration.ofMillis(1));
        fail(breaker, policy, new IllegalArgumentException("business validation"), Duration.ofMillis(1));

        assertThat(breaker.state(policy)).isEqualTo(CircuitBreakerState.CLOSED);
    }

    /**
     * 验证慢调用即使成功也会参与慢调用率熔断。
     */
    @Test
    void shouldOpenAfterSlowCallRateThreshold() {
        Resilience4jCircuitBreaker breaker = new Resilience4jCircuitBreaker();
        CircuitBreakerPolicy policy = policy(Set.of(Exception.class), Set.of(), Duration.ofMillis(10));

        succeed(breaker, policy, Duration.ofMillis(20));
        succeed(breaker, policy, Duration.ofMillis(20));

        assertThat(breaker.state(policy)).isEqualTo(CircuitBreakerState.OPEN);
    }

    /**
     * 验证未记录结果的许可可以幂等关闭。
     */
    @Test
    void shouldReleasePermitIdempotently() {
        Resilience4jCircuitBreaker breaker = new Resilience4jCircuitBreaker();
        CircuitBreakerPermit permit = breaker.acquire(
                policy(Set.of(Exception.class), Set.of(), Duration.ofSeconds(1)));

        assertThatCode(() -> {
            permit.close();
            permit.close();
        }).doesNotThrowAnyException();
    }

    /**
     * 记录一次失败调用。
     *
     * @param breaker 熔断 Provider
     * @param policy 熔断策略
     * @param failure 业务异常
     * @param duration 调用耗时
     */
    private void fail(
            Resilience4jCircuitBreaker breaker,
            CircuitBreakerPolicy policy,
            Throwable failure,
            Duration duration) {
        CircuitBreakerPermit permit = breaker.acquire(policy);
        permit.failure(duration, failure);
        permit.close();
    }

    /**
     * 记录一次成功调用。
     *
     * @param breaker 熔断 Provider
     * @param policy 熔断策略
     * @param duration 调用耗时
     */
    private void succeed(
            Resilience4jCircuitBreaker breaker,
            CircuitBreakerPolicy policy,
            Duration duration) {
        CircuitBreakerPermit permit = breaker.acquire(policy);
        permit.success(duration);
        permit.close();
    }

    /**
     * 创建最少两次调用即可计算阈值的测试策略。
     *
     * @param failureOn 记录失败的异常类型
     * @param ignoreOn 忽略异常类型
     * @param slowThreshold 慢调用阈值
     * @return 熔断策略
     */
    private CircuitBreakerPolicy policy(
            Set<Class<? extends Throwable>> failureOn,
            Set<Class<? extends Throwable>> ignoreOn,
            Duration slowThreshold) {
        return new CircuitBreakerPolicy(
                "inventory-service",
                50F,
                50F,
                slowThreshold,
                2,
                2,
                1,
                Duration.ofSeconds(30),
                false,
                failureOn,
                ignoreOn);
    }
}
