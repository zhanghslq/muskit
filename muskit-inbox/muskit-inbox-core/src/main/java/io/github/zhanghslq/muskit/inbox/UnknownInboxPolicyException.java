package io.github.zhanghslq.muskit.inbox;

/**
 * 表示业务引用了不存在的 Inbox 策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class UnknownInboxPolicyException extends RuntimeException {

    /**
     * 创建未知策略异常。
     *
     * @param policyName 策略名称
     */
    public UnknownInboxPolicyException(String policyName) {
        super("未知 Inbox 策略: " + policyName);
    }
}
