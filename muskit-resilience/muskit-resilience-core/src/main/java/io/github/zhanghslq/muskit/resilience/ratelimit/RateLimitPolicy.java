package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * 不可变令牌桶限流策略。
 *
 * @param name 低基数策略名称
 * @param capacity 令牌桶容量
 * @param refillTokens 每个补充周期新增令牌数
 * @param refillPeriod 令牌补充周期
 * @param scope 限流隔离范围
 * @author zhs
 * @since 2026-08-20
 */
public record RateLimitPolicy(
        String name,
        int capacity,
        int refillTokens,
        Duration refillPeriod,
        RateLimitScope scope) {

    /**
     * 校验并创建令牌桶限流策略。
     */
    public RateLimitPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("限流策略名称不能为空");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("令牌桶容量必须大于 0");
        }
        if (refillTokens <= 0) {
            throw new IllegalArgumentException("周期补充令牌数必须大于 0");
        }
        Objects.requireNonNull(refillPeriod, "令牌补充周期不能为空");
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("令牌补充周期必须大于 0");
        }
        Objects.requireNonNull(scope, "限流隔离范围不能为空");
    }
}
