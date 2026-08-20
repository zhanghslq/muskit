package io.github.zhanghslq.muskit.resilience.autoconfigure;

/**
 * 令牌桶限流 Provider 类型。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum RateLimitProviderType {

    /** 使用当前 JVM 内的令牌桶。 */
    LOCAL,

    /** 使用 Redis 原子令牌桶。 */
    REDIS
}
