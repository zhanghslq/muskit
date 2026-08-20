package io.github.zhanghslq.muskit.concurrency.autoconfigure;

/**
 * 并发额度 Provider 类型。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum ConcurrencyProviderType {

    /** 单 JVM 本地并发控制。 */
    LOCAL,

    /** 基于 Redis 的跨实例并发控制。 */
    REDIS
}
