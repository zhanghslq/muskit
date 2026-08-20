package io.github.zhanghslq.muskit.resilience.retry;

/**
 * 引用了不存在的重试策略时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class UnknownRetryPolicyException extends IllegalArgumentException {

    /**
     * 使用低基数策略名称创建异常。
     *
     * @param policyName 策略名称
     */
    public UnknownRetryPolicyException(String policyName) {
        super("未找到重试策略: " + policyName);
    }
}
