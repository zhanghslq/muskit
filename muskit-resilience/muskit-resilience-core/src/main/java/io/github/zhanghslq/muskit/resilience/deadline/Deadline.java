package io.github.zhanghslq.muskit.resilience.deadline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 表示调用链不可变的绝对截止时间。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class Deadline {

    private final Instant expiresAt;
    private final Clock clock;

    /**
     * 使用系统 UTC 时钟创建指定时长后的 Deadline。
     *
     * @param timeout 剩余时间
     * @return Deadline
     */
    public static Deadline after(Duration timeout) {
        return after(timeout, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建指定时长后的 Deadline。
     *
     * @param timeout 剩余时间
     * @param clock 时钟
     * @return Deadline
     */
    public static Deadline after(Duration timeout, Clock clock) {
        Objects.requireNonNull(timeout, "Deadline 超时时间不能为空");
        Objects.requireNonNull(clock, "Deadline 时钟不能为空");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Deadline 超时时间不能为负数");
        }
        return new Deadline(clock.instant().plus(timeout), clock);
    }

    /**
     * 使用系统 UTC 时钟创建绝对时间 Deadline。
     *
     * @param expiresAt 截止时间
     * @return Deadline
     */
    public static Deadline at(Instant expiresAt) {
        return new Deadline(expiresAt, Clock.systemUTC());
    }

    /**
     * 创建绝对时间 Deadline。
     *
     * @param expiresAt 截止时间
     * @param clock 判断剩余时间使用的时钟
     */
    Deadline(Instant expiresAt, Clock clock) {
        this.expiresAt = Objects.requireNonNull(expiresAt, "Deadline 截止时间不能为空");
        this.clock = Objects.requireNonNull(clock, "Deadline 时钟不能为空");
    }

    /**
     * 返回绝对截止时间。
     *
     * @return 截止时间
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * 返回非负剩余时间。
     *
     * @return 剩余时间，到期后为零
     */
    public Duration remaining() {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * 返回 Deadline 是否已经到期。
     *
     * @return 是否到期
     */
    public boolean isExpired() {
        return !clock.instant().isBefore(expiresAt);
    }

    /**
     * 到期时抛出稳定公共异常。
     */
    public void throwIfExpired() {
        if (isExpired()) {
            throw new DeadlineExceededException();
        }
    }

    /**
     * 返回两个 Deadline 中更早的一个，禁止子调用延长父调用预算。
     *
     * @param other 另一个 Deadline
     * @return 更早的 Deadline
     */
    public Deadline earliest(Deadline other) {
        Objects.requireNonNull(other, "待比较 Deadline 不能为空");
        return expiresAt.isAfter(other.expiresAt) ? other : this;
    }
}
