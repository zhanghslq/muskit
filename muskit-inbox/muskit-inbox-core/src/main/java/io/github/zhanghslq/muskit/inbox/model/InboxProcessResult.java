package io.github.zhanghslq.muskit.inbox.model;

/**
 * Inbox 处理器没有抛出异常时的稳定结果。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum InboxProcessResult {

    /** 本次调用真实执行并成功提交业务处理。 */
    PROCESSED,

    /** 成功状态仍在保留期内，本次跳过重复消息。 */
    DUPLICATE,

    /** 消息已经是死信，本次未再执行业务。 */
    DEAD
}
