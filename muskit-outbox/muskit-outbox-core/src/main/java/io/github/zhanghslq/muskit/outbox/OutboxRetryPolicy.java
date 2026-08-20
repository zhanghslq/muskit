package io.github.zhanghslq.muskit.outbox;

import java.time.Duration;
import java.util.Objects;

/**
 * Outbox 发布最大尝试次数和有上限指数退避策略。
 *
 * @param maxAttempts 最大发布尝试次数
 * @param initialDelay 首次失败等待时间
 * @param multiplier 退避倍数
 * @param maxDelay 最大等待时间
 * @author zhs
 * @since 2026-08-20
 */
public record OutboxRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay) {

    /**
     * 校验并创建 Outbox 重试策略。
     */
    public OutboxRetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Outbox 最大发布次数必须大于 0");
        }
        requireNonNegative(initialDelay, "Outbox 首次重试等待时间不能为负数");
        if (!Double.isFinite(multiplier) || multiplier < 1D) {
            throw new IllegalArgumentException("Outbox 重试倍数必须是大于等于 1 的有限数");
        }
        requireNonNegative(maxDelay, "Outbox 最大重试等待时间不能为负数");
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Outbox 最大重试等待时间不能小于首次等待时间");
        }
    }

    /**
     * 计算指定失败次数后的有上限指数退避。
     *
     * @param attempt 当前失败次数，从一开始
     * @return 重试等待时间
     */
    public Duration delayAfterFailure(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("Outbox 当前失败次数必须大于 0");
        }
        long initialNanos = saturatedNanos(initialDelay);
        long maximumNanos = saturatedNanos(maxDelay);
        double calculated = initialNanos * Math.pow(multiplier, Math.max(0, attempt - 1));
        return Duration.ofNanos((long) Math.min(maximumNanos, calculated));
    }

    /**
     * 校验非负时间。
     *
     * @param duration 时间值
     * @param message 错误消息
     */
    private static void requireNonNegative(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 将时间转换为饱和纳秒值。
     *
     * @param duration 时间值
     * @return 纳秒值
     */
    private static long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
