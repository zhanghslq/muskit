package io.github.zhanghslq.muskit.inbox.model;

/**
 * Inbox 存储对一次消息处理请求的原子判定。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum InboxDecision {

    /** 当前调用取得处理所有权。 */
    ACQUIRED,

    /** 消息已经成功处理。 */
    SUCCEEDED,

    /** 另一个调用仍持有有效处理租约。 */
    IN_PROGRESS,

    /** 消息尚未到重试时间。 */
    RETRY_LATER,

    /** 消息已经进入死信状态。 */
    DEAD
}
