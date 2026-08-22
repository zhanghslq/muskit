package io.github.zhanghslq.muskit.cache.autoconfigure;

import io.github.zhanghslq.muskit.cache.exception.UnknownCachePolicyException;
import io.github.zhanghslq.muskit.cache.model.CachePolicy;
import io.github.zhanghslq.muskit.cache.spi.CachePolicyResolver;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从类型安全配置中解析缓存策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class PropertiesCachePolicyResolver implements CachePolicyResolver {

    private final Map<String, CachePolicy> policies;

    /**
     * 创建配置属性缓存策略解析器。
     *
     * @param properties 缓存配置
     */
    PropertiesCachePolicyResolver(MuskitCacheProperties properties) {
        Map<String, CachePolicy> resolved = new LinkedHashMap<>();
        properties.getPolicies().forEach((name, policy) -> resolved.put(name, new CachePolicy(
                name,
                policy.getTtl(),
                policy.getNullTtl(),
                policy.getTtlJitterRatio(),
                policy.getStaleWhileRevalidate(),
                policy.getFailureMode())));
        this.policies = Map.copyOf(resolved);
    }

    /**
     * 返回指定缓存策略。
     *
     * @param policyName 策略名称
     * @return 缓存策略
     */
    @Override
    public CachePolicy resolve(String policyName) {
        CachePolicy policy = policies.get(policyName);
        if (policy == null) {
            throw new UnknownCachePolicyException(policyName);
        }
        return policy;
    }
}
