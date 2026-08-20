package io.github.zhanghslq.muskit.inbox;

/**
 * 按低基数名称解析 Inbox 策略的可替换 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface InboxPolicyResolver {

    /**
     * 解析指定 Inbox 策略。
     *
     * @param policyName 策略名称
     * @return Inbox 策略
     */
    InboxPolicy resolve(String policyName);
}
