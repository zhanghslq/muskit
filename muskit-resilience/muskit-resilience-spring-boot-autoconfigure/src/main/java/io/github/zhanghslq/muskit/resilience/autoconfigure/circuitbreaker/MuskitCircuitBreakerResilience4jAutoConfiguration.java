package io.github.zhanghslq.muskit.resilience.autoconfigure.circuitbreaker;

import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.resilience4j.Resilience4jCircuitBreaker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Resilience4j 熔断 Provider 自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(before = MuskitCircuitBreakerAutoConfiguration.class)
@ConditionalOnClass({
        io.github.resilience4j.circuitbreaker.CircuitBreaker.class,
        Resilience4jCircuitBreaker.class
})
@ConditionalOnProperty(
        prefix = "muskit.resilience",
        name = "circuit-breaker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MuskitCircuitBreakerResilience4jAutoConfiguration {

    /**
     * 创建 Resilience4j 熔断 Provider 自动配置。
     */
    public MuskitCircuitBreakerResilience4jAutoConfiguration() {
    }

    /**
     * 创建 Resilience4j 熔断 Provider。
     *
     * @return 熔断 Provider
     */
    @Bean
    @ConditionalOnMissingBean(CircuitBreaker.class)
    public CircuitBreaker muskitCircuitBreaker() {
        return new Resilience4jCircuitBreaker();
    }
}
