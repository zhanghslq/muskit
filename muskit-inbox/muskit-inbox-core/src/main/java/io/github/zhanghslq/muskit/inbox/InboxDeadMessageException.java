package io.github.zhanghslq.muskit.inbox;

/**
 * 表示消息在本次失败后达到最大处理次数并进入死信状态。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxDeadMessageException extends RuntimeException {

    /**
     * 创建 Inbox 死信异常。
     *
     * @param policyName 低基数策略名称
     * @param cause 最后一次业务异常
     */
    public InboxDeadMessageException(String policyName, Throwable cause) {
        super("Inbox 消息已进入死信状态: policy=" + policyName, cause);
    }
}
