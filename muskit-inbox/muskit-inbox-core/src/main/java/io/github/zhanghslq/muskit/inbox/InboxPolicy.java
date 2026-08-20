package io.github.zhanghslq.muskit.inbox;

import java.time.Duration;
import java.util.Objects;

/**
 * Inbox 处理租约、成功保留和指数退避策略。
 *
 * @param name 低基数策略名称
 * @param processingTimeout 单次处理租约时间
 * @param retention 成功状态保留时间
 * @param maxAttempts 最大处理次数
 * @param initialRetryDelay 首次重试等待时间
 * @param retryMultiplier 重试退避倍数
 * @param maxRetryDelay 最大重试等待时间
 * @author zhs
 * @since 2026-08-20
 */
public record InboxPolicy(
        String name,
        Duration processingTimeout,
        Duration retention,
        int maxAttempts,
        Duration initialRetryDelay,
        double retryMultiplier,
        Duration maxRetryDelay) {

    /**
     * 校验并创建 Inbox 策略。
     */
    public InboxPolicy {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("Inbox 策略名称不能为空且长度不能超过 128");
        }
        requirePositive(processingTimeout, "Inbox 处理租约必须大于 0");
        requirePositive(retention, "Inbox 成功保留时间必须大于 0");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Inbox 最大处理次数必须大于 0");
        }
        requireNonNegative(initialRetryDelay, "Inbox 首次重试等待时间不能为负数");
        if (!Double.isFinite(retryMultiplier) || retryMultiplier < 1D) {
            throw new IllegalArgumentException("Inbox 重试倍数必须是大于等于 1 的有限数");
        }
        requireNonNegative(maxRetryDelay, "Inbox 最大重试等待时间不能为负数");
        if (maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("Inbox 最大重试等待时间不能小于首次等待时间");
        }
    }

    /**
     * 校验正数时间。
     *
     * @param duration 时间值
     * @param message 错误消息
     */
    private static void requirePositive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
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
}
