package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * 限流判定结果。
 *
 * @param allowed 是否允许本次调用
 * @param retryAfter 被拒绝后建议等待时间，允许时为零
 * @author zhs
 * @since 2026-08-20
 */
public record RateLimitDecision(boolean allowed, Duration retryAfter) {

    /**
     * 校验并创建限流判定结果。
     */
    public RateLimitDecision {
        Objects.requireNonNull(retryAfter, "限流重试等待时间不能为空");
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("限流重试等待时间不能为负数");
        }
        if (allowed && !retryAfter.isZero()) {
            throw new IllegalArgumentException("允许结果的重试等待时间必须为零");
        }
    }

    /**
     * 创建允许调用的结果。
     *
     * @return 允许结果
     */
    public static RateLimitDecision permitted() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    /**
     * 创建拒绝调用的结果。
     *
     * @param retryAfter 建议等待时间
     * @return 拒绝结果
     */
    public static RateLimitDecision rejected(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
