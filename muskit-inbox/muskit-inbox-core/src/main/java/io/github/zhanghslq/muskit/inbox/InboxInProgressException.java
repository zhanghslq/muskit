package io.github.zhanghslq.muskit.inbox;

/**
 * 表示同一业务消息正在由另一个调用处理。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxInProgressException extends RuntimeException {

    /**
     * 创建处理中冲突异常。
     *
     * @param policyName 低基数策略名称
     */
    public InboxInProgressException(String policyName) {
        super("Inbox 消息正在处理中: policy=" + policyName);
    }
}
