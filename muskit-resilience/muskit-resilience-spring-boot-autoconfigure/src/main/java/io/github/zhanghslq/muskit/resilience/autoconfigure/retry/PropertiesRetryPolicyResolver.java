package io.github.zhanghslq.muskit.resilience.autoconfigure.retry;

import io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicy;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicyResolver;
import io.github.zhanghslq.muskit.resilience.retry.RetryPredicate;
import io.github.zhanghslq.muskit.resilience.retry.UnknownRetryPolicyException;
import java.util.Objects;

/**
 * 从 Spring Boot 配置属性解析重试策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class PropertiesRetryPolicyResolver implements RetryPolicyResolver {

    private final MuskitResilienceProperties properties;

    /**
     * 创建配置属性重试策略解析器。
     *
     * @param properties 韧性配置属性
     */
    public PropertiesRetryPolicyResolver(MuskitResilienceProperties properties) {
        this.properties = Objects.requireNonNull(properties, "韧性配置属性不能为空");
    }

    /**
     * 解析指定名称的重试策略。
     *
     * @param policyName 策略名称
     * @return 重试策略
     */
    @Override
    public RetryPolicy resolve(String policyName) {
        MuskitResilienceProperties.RetryPolicyProperties policy =
                properties.getRetryPolicies().get(policyName);
        if (policy == null) {
            throw new UnknownRetryPolicyException(policyName);
        }
        return new RetryPolicy(
                policyName,
                policy.getMaxAttempts(),
                policy.getInitialDelay(),
                policy.getMultiplier(),
                policy.getMaxDelay(),
                policy.getJitter(),
                policy.getRetryOn(),
                policy.getAbortOn(),
                RetryPredicate.always());
    }
}
