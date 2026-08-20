package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.time.Duration;

/**
 * 令牌不足拒绝业务调用时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class RateLimitRejectedException extends RuntimeException {

    /** 触发限流的低基数策略名称。 */
    private final String policyName;

    /** 建议调用方等待后重试的时间。 */
    private final Duration retryAfter;

    /**
     * 使用低基数策略名称和建议等待时间创建限流异常。
     *
     * @param policyName 策略名称
     * @param retryAfter 建议等待时间
     */
    public RateLimitRejectedException(String policyName, Duration retryAfter) {
        super("调用触发限流，策略: " + policyName + "，建议等待: " + retryAfter);
        this.policyName = policyName;
        this.retryAfter = retryAfter;
    }

    /**
     * 返回触发限流的策略名称。
     *
     * @return 策略名称
     */
    public String policyName() {
        return policyName;
    }

    /**
     * 返回建议等待时间。
     *
     * @return 建议等待时间
     */
    public Duration retryAfter() {
        return retryAfter;
    }
}
