package io.github.zhanghslq.muskit.resilience.retry;

/**
 * 同步重试退避等待被中断时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class RetryInterruptedException extends RuntimeException {

    /**
     * 使用低基数策略名称和中断原因创建异常。
     *
     * @param policyName 策略名称
     * @param cause 中断原因
     */
    public RetryInterruptedException(String policyName, InterruptedException cause) {
        super("重试退避等待被中断，策略: " + policyName, cause);
    }
}
