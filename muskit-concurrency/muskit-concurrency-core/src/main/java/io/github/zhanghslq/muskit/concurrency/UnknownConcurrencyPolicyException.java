package io.github.zhanghslq.muskit.concurrency;

/**
 * 找不到指定并发控制策略时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class UnknownConcurrencyPolicyException extends RuntimeException {

    /**
     * 根据策略名称创建异常。
     *
     * @param policyName 策略名称
     */
    public UnknownConcurrencyPolicyException(String policyName) {
        super("未配置并发控制策略: " + policyName);
    }
}

