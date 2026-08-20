package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 限流令牌桶隔离范围。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum RateLimitScope {

    /** 所有请求共享一个令牌桶。 */
    GLOBAL,

    /** 每个业务键使用独立令牌桶。 */
    KEY
}
