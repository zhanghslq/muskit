package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.util.Objects;

/**
 * 单次限流判定请求。
 *
 * @param policy 限流策略
 * @param key 业务隔离键，全局策略可为空
 * @author zhs
 * @since 2026-08-20
 */
public record RateLimitRequest(RateLimitPolicy policy, String key) {

    /**
     * 校验并创建限流请求。
     */
    public RateLimitRequest {
        Objects.requireNonNull(policy, "限流策略不能为空");
        key = key == null ? "" : key;
        if (policy.scope() == RateLimitScope.KEY && key.isBlank()) {
            throw new IllegalArgumentException("KEY 范围限流策略必须提供业务键");
        }
    }

    /**
     * 返回实际令牌桶隔离键。
     *
     * @return 全局策略返回空字符串，按键策略返回业务键
     */
    public String effectiveKey() {
        return policy.scope() == RateLimitScope.GLOBAL ? "" : key;
    }

    /**
     * 返回不包含业务键的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "RateLimitRequest[policy=" + policy.name() + ", scope=" + policy.scope() + "]";
    }
}
