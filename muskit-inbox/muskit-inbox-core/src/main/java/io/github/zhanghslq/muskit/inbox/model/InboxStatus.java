package io.github.zhanghslq.muskit.inbox.model;

/**
 * Inbox 消息持久化状态。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum InboxStatus {

    /** 当前实例持有处理租约。 */
    PROCESSING,

    /** 业务失败并等待下次重试。 */
    RETRY_WAIT,

    /** 业务已经成功处理。 */
    SUCCEEDED,

    /** 已达到最大尝试次数，需要人工回放。 */
    DEAD
}
