package io.github.zhanghslq.muskit.resilience.retry;

/**
 * 根据稳定名称解析重试策略的扩展点。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RetryPolicyResolver {

    /**
     * 解析指定重试策略。
     *
     * @param policyName 策略名称
     * @return 重试策略
     */
    RetryPolicy resolve(String policyName);
}
