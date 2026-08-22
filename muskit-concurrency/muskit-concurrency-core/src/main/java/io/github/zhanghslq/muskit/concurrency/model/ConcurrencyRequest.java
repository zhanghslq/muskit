package io.github.zhanghslq.muskit.concurrency.model;

import java.util.Objects;

/**
 * 单次并发额度获取请求。
 *
 * @param policy 并发控制策略
 * @param key 业务隔离键，全局策略可为空
 * @author zhs
 * @since 2026-08-20
 */
public record ConcurrencyRequest(ConcurrencyPolicy policy, String key) {

    /**
     * 校验并创建并发额度请求。
     */
    public ConcurrencyRequest {
        Objects.requireNonNull(policy, "并发控制策略不能为空");
        key = key == null ? "" : key;
        if (policy.scope() == ConcurrencyScope.KEY && key.isBlank()) {
            throw new IllegalArgumentException("KEY 范围并发策略必须提供业务键");
        }
    }

    /**
     * 返回用于定位本地并发槽位的有效业务键。
     *
     * @return 全局策略返回空字符串，按键策略返回业务键
     */
    public String effectiveKey() {
        return policy.scope() == ConcurrencyScope.GLOBAL ? "" : key;
    }
}

