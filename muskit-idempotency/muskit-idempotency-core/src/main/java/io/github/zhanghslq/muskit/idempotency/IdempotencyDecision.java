package io.github.zhanghslq.muskit.idempotency;

/**
 * 表示幂等状态机对一次开始请求的判定。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum IdempotencyDecision {

    /** 当前调用已取得处理权。 */
    ACQUIRED,

    /** 另一调用正在处理同一幂等请求。 */
    IN_PROGRESS,

    /** 同一幂等请求已经处理完成。 */
    COMPLETED
}
