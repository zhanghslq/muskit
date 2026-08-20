package io.github.zhanghslq.muskit.resilience.circuitbreaker;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变熔断状态机策略。
 *
 * @param name 低基数策略名称
 * @param failureRateThreshold 失败率阈值百分比
 * @param slowCallRateThreshold 慢调用率阈值百分比
 * @param slowCallDurationThreshold 慢调用耗时阈值
 * @param minimumNumberOfCalls 计算阈值前的最少调用数
 * @param slidingWindowSize 计数滑动窗口大小
 * @param permittedCallsInHalfOpen 半开状态允许的探测调用数
 * @param waitDurationInOpenState 开启状态等待时间
 * @param automaticTransition 是否自动从开启状态进入半开状态
 * @param failureOn 记录为失败的异常类型
 * @param ignoreOn 完全忽略的异常类型，优先级高于 failureOn
 * @author zhs
 * @since 2026-08-20
 */
public record CircuitBreakerPolicy(
        String name,
        float failureRateThreshold,
        float slowCallRateThreshold,
        Duration slowCallDurationThreshold,
        int minimumNumberOfCalls,
        int slidingWindowSize,
        int permittedCallsInHalfOpen,
        Duration waitDurationInOpenState,
        boolean automaticTransition,
        Set<Class<? extends Throwable>> failureOn,
        Set<Class<? extends Throwable>> ignoreOn) {

    /**
     * 校验并创建熔断策略。
     */
    public CircuitBreakerPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("熔断策略名称不能为空");
        }
        validateRate(failureRateThreshold, "失败率阈值");
        validateRate(slowCallRateThreshold, "慢调用率阈值");
        Objects.requireNonNull(slowCallDurationThreshold, "慢调用耗时阈值不能为空");
        Objects.requireNonNull(waitDurationInOpenState, "熔断开启等待时间不能为空");
        if (slowCallDurationThreshold.isZero() || slowCallDurationThreshold.isNegative()) {
            throw new IllegalArgumentException("慢调用耗时阈值必须大于 0");
        }
        if (waitDurationInOpenState.isZero() || waitDurationInOpenState.isNegative()) {
            throw new IllegalArgumentException("熔断开启等待时间必须大于 0");
        }
        if (minimumNumberOfCalls <= 0 || slidingWindowSize <= 0) {
            throw new IllegalArgumentException("最少调用数和滑动窗口大小必须大于 0");
        }
        if (minimumNumberOfCalls > slidingWindowSize) {
            throw new IllegalArgumentException("最少调用数不能大于滑动窗口大小");
        }
        if (permittedCallsInHalfOpen <= 0) {
            throw new IllegalArgumentException("半开状态探测调用数必须大于 0");
        }
        failureOn = Set.copyOf(Objects.requireNonNull(failureOn, "失败异常类型不能为空"));
        ignoreOn = Set.copyOf(Objects.requireNonNull(ignoreOn, "忽略异常类型不能为空"));
    }

    /**
     * 判断异常是否应计入失败率。
     *
     * @param failure 业务异常
     * @return 是否记录失败
     */
    public boolean shouldRecordFailure(Throwable failure) {
        Objects.requireNonNull(failure, "熔断异常不能为空");
        return !shouldIgnore(failure) && matches(failureOn, failure);
    }

    /**
     * 判断异常是否完全不进入熔断统计。
     *
     * @param failure 业务异常
     * @return 是否忽略
     */
    public boolean shouldIgnore(Throwable failure) {
        Objects.requireNonNull(failure, "熔断异常不能为空");
        return matches(ignoreOn, failure);
    }

    /**
     * 校验百分比阈值。
     *
     * @param rate 百分比值
     * @param description 配置描述
     */
    private static void validateRate(float rate, String description) {
        if (!Float.isFinite(rate) || rate <= 0F || rate > 100F) {
            throw new IllegalArgumentException(description + "必须在 (0, 100] 范围内");
        }
    }

    /**
     * 判断异常是否属于任一配置类型。
     *
     * @param types 异常类型集合
     * @param failure 业务异常
     * @return 是否匹配
     */
    private boolean matches(Set<Class<? extends Throwable>> types, Throwable failure) {
        return types.stream().anyMatch(type -> type.isInstance(failure));
    }
}
