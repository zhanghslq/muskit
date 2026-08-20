package io.github.zhanghslq.muskit.concurrency.autoconfigure;

import java.util.Objects;

import io.github.zhanghslq.muskit.concurrency.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyPolicyResolver;
import io.github.zhanghslq.muskit.concurrency.UnknownConcurrencyPolicyException;

/**
 * 从 Spring Boot 配置属性中解析并发策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class PropertiesConcurrencyPolicyResolver implements ConcurrencyPolicyResolver {

    private final MuskitConcurrencyProperties properties;

    /**
     * 创建配置属性并发策略解析器。
     *
     * @param properties 并发控制配置属性
     */
    public PropertiesConcurrencyPolicyResolver(MuskitConcurrencyProperties properties) {
        this.properties = Objects.requireNonNull(properties, "并发控制配置属性不能为空");
    }

    /**
     * 解析指定名称的并发控制策略。
     *
     * @param policyName 策略名称
     * @return 并发控制策略
     */
    @Override
    public ConcurrencyPolicy resolve(String policyName) {
        MuskitConcurrencyProperties.PolicyProperties policy = properties.getPolicies().get(policyName);
        if (policy == null) {
            throw new UnknownConcurrencyPolicyException(policyName);
        }
        return new ConcurrencyPolicy(
                policyName,
                policy.getMaxConcurrency(),
                policy.getMaxWait(),
                policy.getScope(),
                policy.isFair());
    }
}

