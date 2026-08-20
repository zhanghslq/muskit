package io.github.zhanghslq.muskit.resilience.ratelimit;

/**
 * 限流后端不可用或执行失败时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class RateLimitBackendException extends RuntimeException {

    /**
     * 使用低基数策略名称和后端原因创建异常。
     *
     * @param policyName 策略名称
     * @param cause 后端异常
     */
    public RateLimitBackendException(String policyName, Throwable cause) {
        super("限流后端执行失败，策略: " + policyName, cause);
    }
}
