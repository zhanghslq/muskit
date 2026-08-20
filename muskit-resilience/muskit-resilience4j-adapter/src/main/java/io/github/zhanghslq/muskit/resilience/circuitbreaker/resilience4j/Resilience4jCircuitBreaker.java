package io.github.zhanghslq.muskit.resilience.circuitbreaker.resilience4j;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerOpenException;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPermit;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicy;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerState;

/**
 * 将 Muskit 熔断 SPI 适配到 Resilience4j 成熟状态机。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class Resilience4jCircuitBreaker implements CircuitBreaker {

    private final ConcurrentMap<String, Entry> breakers = new ConcurrentHashMap<>();

    /**
     * 创建 Resilience4j 熔断 Provider。
     */
    public Resilience4jCircuitBreaker() {
    }

    /**
     * 获取 Resilience4j 调用许可，开启或半开额度耗尽时统一映射为公共异常。
     *
     * @param policy 熔断策略
     * @return 调用许可
     */
    @Override
    public CircuitBreakerPermit acquire(CircuitBreakerPolicy policy) {
        io.github.resilience4j.circuitbreaker.CircuitBreaker delegate = breaker(policy);
        if (!delegate.tryAcquirePermission()) {
            throw new CircuitBreakerOpenException(policy.name());
        }
        return new Resilience4jPermit(delegate);
    }

    /**
     * 返回指定策略当前的统一熔断状态。
     *
     * @param policy 熔断策略
     * @return 当前状态
     */
    @Override
    public CircuitBreakerState state(CircuitBreakerPolicy policy) {
        return switch (breaker(policy).getState()) {
            case CLOSED -> CircuitBreakerState.CLOSED;
            case OPEN -> CircuitBreakerState.OPEN;
            case HALF_OPEN -> CircuitBreakerState.HALF_OPEN;
            case METRICS_ONLY -> CircuitBreakerState.METRICS_ONLY;
            case DISABLED -> CircuitBreakerState.DISABLED;
            case FORCED_OPEN -> CircuitBreakerState.FORCED_OPEN;
        };
    }

    /**
     * 获取策略对应状态机，配置变化时以新状态机替换旧实例。
     *
     * @param policy 熔断策略
     * @return Resilience4j 状态机
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreaker breaker(CircuitBreakerPolicy policy) {
        Objects.requireNonNull(policy, "熔断策略不能为空");
        Entry entry = breakers.compute(policy.name(), (name, current) -> {
            if (current != null && current.policy.equals(policy)) {
                return current;
            }
            return new Entry(policy, createBreaker(policy));
        });
        return entry.breaker;
    }

    /**
     * 根据统一策略创建 Resilience4j 计数滑动窗口状态机。
     *
     * @param policy 熔断策略
     * @return Resilience4j 状态机
     */
    private io.github.resilience4j.circuitbreaker.CircuitBreaker createBreaker(CircuitBreakerPolicy policy) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(policy.failureRateThreshold())
                .slowCallRateThreshold(policy.slowCallRateThreshold())
                .slowCallDurationThreshold(policy.slowCallDurationThreshold())
                .minimumNumberOfCalls(policy.minimumNumberOfCalls())
                .slidingWindowSize(policy.slidingWindowSize())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .permittedNumberOfCallsInHalfOpenState(policy.permittedCallsInHalfOpen())
                .waitDurationInOpenState(policy.waitDurationInOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(policy.automaticTransition())
                .recordException(policy::shouldRecordFailure)
                .ignoreException(policy::shouldIgnore)
                .build();
        return io.github.resilience4j.circuitbreaker.CircuitBreaker.of(policy.name(), config);
    }

    /**
     * 保存策略快照及其状态机。
     *
     * @param policy 策略快照
     * @param breaker 状态机
     * @author zhs
     * @since 2026-08-20
     */
    private record Entry(
            CircuitBreakerPolicy policy,
            io.github.resilience4j.circuitbreaker.CircuitBreaker breaker) {
    }

    /**
     * 将调用结果幂等记录到 Resilience4j 的许可句柄。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class Resilience4jPermit implements CircuitBreakerPermit {

        private final io.github.resilience4j.circuitbreaker.CircuitBreaker delegate;
        private final AtomicBoolean completed = new AtomicBoolean();

        /**
         * 创建 Resilience4j 调用许可。
         *
         * @param delegate Resilience4j 状态机
         */
        private Resilience4jPermit(io.github.resilience4j.circuitbreaker.CircuitBreaker delegate) {
            this.delegate = delegate;
        }

        /**
         * 幂等记录成功调用。
         *
         * @param duration 调用耗时
         */
        @Override
        public void success(Duration duration) {
            Objects.requireNonNull(duration, "熔断调用耗时不能为空");
            if (completed.compareAndSet(false, true)) {
                delegate.onSuccess(saturatedNanos(duration), TimeUnit.NANOSECONDS);
            }
        }

        /**
         * 幂等记录失败调用。
         *
         * @param duration 调用耗时
         * @param failure 业务异常
         */
        @Override
        public void failure(Duration duration, Throwable failure) {
            Objects.requireNonNull(duration, "熔断调用耗时不能为空");
            Objects.requireNonNull(failure, "熔断调用异常不能为空");
            if (completed.compareAndSet(false, true)) {
                delegate.onError(saturatedNanos(duration), TimeUnit.NANOSECONDS, failure);
            }
        }

        /**
         * 未记录结果时幂等释放半开状态调用许可。
         */
        @Override
        public void close() {
            if (completed.compareAndSet(false, true)) {
                delegate.releasePermission();
            }
        }

        /**
         * 将 Duration 转换为饱和纳秒数。
         *
         * @param duration 时间长度
         * @return 饱和纳秒数
         */
        private long saturatedNanos(Duration duration) {
            try {
                return duration.toNanos();
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
    }
}
