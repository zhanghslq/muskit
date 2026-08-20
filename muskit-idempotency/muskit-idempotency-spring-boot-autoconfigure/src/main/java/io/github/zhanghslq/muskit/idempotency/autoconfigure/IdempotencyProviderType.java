package io.github.zhanghslq.muskit.idempotency.autoconfigure;

/**
 * 幂等状态存储 Provider 类型。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum IdempotencyProviderType {

    /** 使用 Redis 作为幂等状态存储。 */
    REDIS,

    /** 使用关系型数据库作为幂等状态存储。 */
    JDBC
}
