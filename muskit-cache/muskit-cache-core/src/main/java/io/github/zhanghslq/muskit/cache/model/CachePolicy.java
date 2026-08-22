package io.github.zhanghslq.muskit.cache.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 可靠缓存的 TTL、空值、抖动、旧值刷新和失败策略。
 *
 * @param name 低基数策略名称
 * @param ttl 正常值新鲜时间
 * @param nullTtl 空值新鲜时间
 * @param ttlJitterRatio TTL 对称抖动比例
 * @param staleWhileRevalidate 允许返回旧值并异步刷新的时间
 * @param failureMode 后端失败语义
 * @author zhs
 * @since 2026-08-20
 */
public record CachePolicy(
        String name,
        Duration ttl,
        Duration nullTtl,
        double ttlJitterRatio,
        Duration staleWhileRevalidate,
        CacheFailureMode failureMode) {

    /**
     * 校验并创建缓存策略。
     */
    public CachePolicy {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new IllegalArgumentException("缓存策略名称不能为空且长度不能超过 128");
        }
        requirePositive(ttl, "缓存 TTL 必须大于 0");
        requirePositive(nullTtl, "缓存空值 TTL 必须大于 0");
        if (!Double.isFinite(ttlJitterRatio) || ttlJitterRatio < 0D || ttlJitterRatio >= 1D) {
            throw new IllegalArgumentException("缓存 TTL 抖动比例必须位于 [0, 1) 区间");
        }
        Objects.requireNonNull(staleWhileRevalidate, "缓存旧值刷新时间不能为空");
        if (staleWhileRevalidate.isNegative()) {
            throw new IllegalArgumentException("缓存旧值刷新时间不能为负数");
        }
        Objects.requireNonNull(failureMode, "缓存失败模式不能为空");
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
}
