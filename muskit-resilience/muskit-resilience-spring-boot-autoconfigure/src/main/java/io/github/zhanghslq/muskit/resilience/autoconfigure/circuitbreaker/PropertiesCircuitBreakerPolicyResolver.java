package io.github.zhanghslq.muskit.resilience.autoconfigure.circuitbreaker;

import io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicy;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicyResolver;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.UnknownCircuitBreakerPolicyException;
import java.util.Objects;

/**
 * 从 Spring Boot 配置属性解析熔断策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class PropertiesCircuitBreakerPolicyResolver implements CircuitBreakerPolicyResolver {

    private final MuskitResilienceProperties properties;

    /**
     * 创建配置属性熔断策略解析器。
     *
     * @param properties 韧性配置属性
     */
    public PropertiesCircuitBreakerPolicyResolver(MuskitResilienceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "韧性配置属性不能为空");
    }

    /**
     * 解析指定名称的熔断策略。
     *
     * @param policyName 策略名称
     * @return 熔断策略
     */
    @Override
    public CircuitBreakerPolicy resolve(String policyName) {
        MuskitResilienceProperties.CircuitBreakerPolicyProperties policy =
                properties.getCircuitBreakerPolicies().get(policyName);
        if (policy == null) {
            throw new UnknownCircuitBreakerPolicyException(policyName);
        }
        return new CircuitBreakerPolicy(
                policyName,
                policy.getFailureRateThreshold(),
                policy.getSlowCallRateThreshold(),
                policy.getSlowCallDurationThreshold(),
                policy.getMinimumNumberOfCalls(),
                policy.getSlidingWindowSize(),
                policy.getPermittedCallsInHalfOpen(),
                policy.getWaitDurationInOpenState(),
                policy.isAutomaticTransition(),
                policy.getFailureOn(),
                policy.getIgnoreOn());
    }
}
