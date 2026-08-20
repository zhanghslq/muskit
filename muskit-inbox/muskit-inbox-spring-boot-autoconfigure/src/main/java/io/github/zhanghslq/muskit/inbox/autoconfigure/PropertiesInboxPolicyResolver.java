package io.github.zhanghslq.muskit.inbox.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.zhanghslq.muskit.inbox.InboxPolicy;
import io.github.zhanghslq.muskit.inbox.InboxPolicyResolver;
import io.github.zhanghslq.muskit.inbox.UnknownInboxPolicyException;

/**
 * 从类型安全配置中解析 Inbox 策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class PropertiesInboxPolicyResolver implements InboxPolicyResolver {

    private final Map<String, InboxPolicy> policies;

    /**
     * 创建配置属性策略解析器。
     *
     * @param properties Inbox 配置
     */
    PropertiesInboxPolicyResolver(MuskitInboxProperties properties) {
        Map<String, InboxPolicy> resolved = new LinkedHashMap<>();
        properties.getPolicies().forEach((name, policy) -> resolved.put(name, new InboxPolicy(
                name,
                policy.getProcessingTimeout(),
                policy.getRetention(),
                policy.getMaxAttempts(),
                policy.getInitialRetryDelay(),
                policy.getRetryMultiplier(),
                policy.getMaxRetryDelay())));
        this.policies = Map.copyOf(resolved);
    }

    /**
     * 返回指定配置策略。
     *
     * @param policyName 策略名称
     * @return Inbox 策略
     */
    @Override
    public InboxPolicy resolve(String policyName) {
        InboxPolicy policy = policies.get(policyName);
        if (policy == null) {
            throw new UnknownInboxPolicyException(policyName);
        }
        return policy;
    }
}
