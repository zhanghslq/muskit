package io.github.zhanghslq.muskit.resilience.autoconfigure.ratelimit;

import io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicy;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.UnknownRateLimitPolicyException;
import java.util.Objects;

/**
 * 从 Spring Boot 配置属性解析限流策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class PropertiesRateLimitPolicyResolver implements RateLimitPolicyResolver {

    private final MuskitResilienceProperties properties;

    /**
     * 创建配置属性限流策略解析器。
     *
     * @param properties 韧性配置属性
     */
    public PropertiesRateLimitPolicyResolver(MuskitResilienceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "韧性配置属性不能为空");
    }

    /**
     * 解析指定名称的令牌桶限流策略。
     *
     * @param policyName 策略名称
     * @return 限流策略
     */
    @Override
    public RateLimitPolicy resolve(String policyName) {
        MuskitResilienceProperties.RateLimitPolicyProperties policy =
                properties.getRateLimitPolicies().get(policyName);
        if (policy == null) {
            throw new UnknownRateLimitPolicyException(policyName);
        }
        return new RateLimitPolicy(
                policyName,
                policy.getCapacity(),
                policy.getRefillTokens(),
                policy.getRefillPeriod(),
                policy.getScope());
    }
}
