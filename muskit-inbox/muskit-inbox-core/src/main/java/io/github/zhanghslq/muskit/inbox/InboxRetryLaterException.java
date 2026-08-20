package io.github.zhanghslq.muskit.inbox;

import java.time.Duration;

/**
 * 表示消息尚未到 Inbox 重试时间。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxRetryLaterException extends RuntimeException {

    private final Duration retryAfter;

    /**
     * 创建等待重试异常。
     *
     * @param policyName 低基数策略名称
     * @param retryAfter 剩余等待时间
     */
    public InboxRetryLaterException(String policyName, Duration retryAfter) {
        super("Inbox 消息尚未到重试时间: policy=" + policyName);
        this.retryAfter = retryAfter;
    }

    /**
     * 返回建议等待时间。
     *
     * @return 剩余等待时间
     */
    public Duration retryAfter() {
        return retryAfter;
    }
}
