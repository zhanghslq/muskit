package io.github.zhanghslq.muskit.resilience.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变指数退避重试策略。
 *
 * @param name 低基数策略名称
 * @param maxAttempts 最大调用次数，包含首次调用
 * @param initialDelay 首次重试等待时间
 * @param multiplier 指数退避倍数
 * @param maxDelay 单次等待时间上限
 * @param jitter 随机抖动比例，范围为零到一之间
 * @param retryOn 允许重试的异常类型
 * @param abortOn 禁止重试的异常类型，优先级高于 retryOn
 * @param predicate 额外异常判定器
 * @author zhs
 * @since 2026-08-20
 */
public record RetryPolicy(
        String name,
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Duration maxDelay,
        double jitter,
        Set<Class<? extends Throwable>> retryOn,
        Set<Class<? extends Throwable>> abortOn,
        RetryPredicate predicate) {

    /**
     * 校验并创建重试策略。
     */
    public RetryPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("重试策略名称不能为空");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("最大重试调用次数必须大于 0");
        }
        Objects.requireNonNull(initialDelay, "首次重试等待时间不能为空");
        Objects.requireNonNull(maxDelay, "最大重试等待时间不能为空");
        if (initialDelay.isNegative() || maxDelay.isNegative()) {
            throw new IllegalArgumentException("重试等待时间不能为负数");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("最大重试等待时间不能小于首次等待时间");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1D) {
            throw new IllegalArgumentException("重试退避倍数必须是不小于 1 的有限数");
        }
        if (!Double.isFinite(jitter) || jitter < 0D || jitter >= 1D) {
            throw new IllegalArgumentException("重试抖动比例必须在 [0, 1) 范围内");
        }
        retryOn = Set.copyOf(Objects.requireNonNull(retryOn, "允许重试异常类型不能为空"));
        abortOn = Set.copyOf(Objects.requireNonNull(abortOn, "禁止重试异常类型不能为空"));
        predicate = Objects.requireNonNull(predicate, "重试额外判定器不能为空");
    }

    /**
     * 判断失败是否同时满足禁止、允许和扩展判定规则。
     *
     * @param failure 调用异常
     * @return 是否允许重试
     */
    public boolean shouldRetry(Throwable failure) {
        Objects.requireNonNull(failure, "重试异常不能为空");
        if (matches(abortOn, failure)) {
            return false;
        }
        return matches(retryOn, failure) && predicate.shouldRetry(failure);
    }

    /**
     * 判断异常是否属于任一配置类型。
     *
     * @param types 异常类型集合
     * @param failure 调用异常
     * @return 是否匹配
     */
    private boolean matches(Set<Class<? extends Throwable>> types, Throwable failure) {
        return types.stream().anyMatch(type -> type.isInstance(failure));
    }
}
