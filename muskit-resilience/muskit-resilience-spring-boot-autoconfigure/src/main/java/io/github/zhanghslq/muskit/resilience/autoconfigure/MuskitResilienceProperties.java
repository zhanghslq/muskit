package io.github.zhanghslq.muskit.resilience.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Muskit 韧性能力配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.resilience")
public class MuskitResilienceProperties {

    private boolean rateLimitEnabled = true;
    private RateLimitProviderType rateLimitProvider = RateLimitProviderType.LOCAL;
    private int rateLimitOrder = Ordered.HIGHEST_PRECEDENCE + 120;
    private int maxLocalBuckets = 100_000;
    private Duration localBucketIdleRetention = Duration.ofMinutes(10);
    private String redisRateLimitKeyPrefix = "muskit:rate-limit:";
    private Map<String, RateLimitPolicyProperties> rateLimitPolicies = new LinkedHashMap<>();
    private boolean retryEnabled = true;
    private int retryOrder = Ordered.HIGHEST_PRECEDENCE + 140;
    private Map<String, RetryPolicyProperties> retryPolicies = new LinkedHashMap<>();
    private boolean circuitBreakerEnabled = true;
    private int circuitBreakerOrder = Ordered.HIGHEST_PRECEDENCE + 160;
    private Map<String, CircuitBreakerPolicyProperties> circuitBreakerPolicies = new LinkedHashMap<>();

    /**
     * 创建 Muskit 韧性配置属性。
     */
    public MuskitResilienceProperties() {
    }

    /**
     * 返回注解限流是否启用。
     *
     * @return 是否启用
     */
    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    /**
     * 设置注解限流是否启用。
     *
     * @param rateLimitEnabled 是否启用
     */
    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    /**
     * 返回令牌桶限流 Provider 类型。
     *
     * @return Provider 类型
     */
    public RateLimitProviderType getRateLimitProvider() {
        return rateLimitProvider;
    }

    /**
     * 设置令牌桶限流 Provider 类型。
     *
     * @param rateLimitProvider Provider 类型
     */
    public void setRateLimitProvider(RateLimitProviderType rateLimitProvider) {
        this.rateLimitProvider = rateLimitProvider;
    }

    /**
     * 返回限流切面顺序。
     *
     * @return 切面顺序
     */
    public int getRateLimitOrder() {
        return rateLimitOrder;
    }

    /**
     * 设置限流切面顺序。
     *
     * @param rateLimitOrder 切面顺序
     */
    public void setRateLimitOrder(int rateLimitOrder) {
        this.rateLimitOrder = rateLimitOrder;
    }

    /**
     * 返回本地限流桶数量上限。
     *
     * @return 桶数量上限
     */
    public int getMaxLocalBuckets() {
        return maxLocalBuckets;
    }

    /**
     * 设置本地限流桶数量上限。
     *
     * @param maxLocalBuckets 桶数量上限
     */
    public void setMaxLocalBuckets(int maxLocalBuckets) {
        this.maxLocalBuckets = maxLocalBuckets;
    }

    /**
     * 返回本地空闲限流桶保留时间。
     *
     * @return 空闲保留时间
     */
    public Duration getLocalBucketIdleRetention() {
        return localBucketIdleRetention;
    }

    /**
     * 设置本地空闲限流桶保留时间。
     *
     * @param localBucketIdleRetention 空闲保留时间
     */
    public void setLocalBucketIdleRetention(Duration localBucketIdleRetention) {
        this.localBucketIdleRetention = localBucketIdleRetention;
    }

    /**
     * 返回 Redis 限流键前缀。
     *
     * @return Redis 键前缀
     */
    public String getRedisRateLimitKeyPrefix() {
        return redisRateLimitKeyPrefix;
    }

    /**
     * 设置 Redis 限流键前缀。
     *
     * @param redisRateLimitKeyPrefix Redis 键前缀
     */
    public void setRedisRateLimitKeyPrefix(String redisRateLimitKeyPrefix) {
        this.redisRateLimitKeyPrefix = redisRateLimitKeyPrefix;
    }

    /**
     * 返回按名称配置的限流策略。
     *
     * @return 限流策略映射
     */
    public Map<String, RateLimitPolicyProperties> getRateLimitPolicies() {
        return rateLimitPolicies;
    }

    /**
     * 设置按名称配置的限流策略。
     *
     * @param rateLimitPolicies 限流策略映射
     */
    public void setRateLimitPolicies(Map<String, RateLimitPolicyProperties> rateLimitPolicies) {
        this.rateLimitPolicies = rateLimitPolicies == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(rateLimitPolicies);
    }

    /**
     * 返回注解重试是否启用。
     *
     * @return 是否启用
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    /**
     * 设置注解重试是否启用。
     *
     * @param retryEnabled 是否启用
     */
    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    /**
     * 返回重试切面顺序。
     *
     * @return 切面顺序
     */
    public int getRetryOrder() {
        return retryOrder;
    }

    /**
     * 设置重试切面顺序。
     *
     * @param retryOrder 切面顺序
     */
    public void setRetryOrder(int retryOrder) {
        this.retryOrder = retryOrder;
    }

    /**
     * 返回按名称配置的重试策略。
     *
     * @return 重试策略映射
     */
    public Map<String, RetryPolicyProperties> getRetryPolicies() {
        return retryPolicies;
    }

    /**
     * 设置按名称配置的重试策略。
     *
     * @param retryPolicies 重试策略映射
     */
    public void setRetryPolicies(Map<String, RetryPolicyProperties> retryPolicies) {
        this.retryPolicies = retryPolicies == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(retryPolicies);
    }

    /**
     * 返回注解熔断是否启用。
     *
     * @return 是否启用
     */
    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    /**
     * 设置注解熔断是否启用。
     *
     * @param circuitBreakerEnabled 是否启用
     */
    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    /**
     * 返回熔断切面顺序。
     *
     * @return 切面顺序
     */
    public int getCircuitBreakerOrder() {
        return circuitBreakerOrder;
    }

    /**
     * 设置熔断切面顺序。
     *
     * @param circuitBreakerOrder 切面顺序
     */
    public void setCircuitBreakerOrder(int circuitBreakerOrder) {
        this.circuitBreakerOrder = circuitBreakerOrder;
    }

    /**
     * 返回按名称配置的熔断策略。
     *
     * @return 熔断策略映射
     */
    public Map<String, CircuitBreakerPolicyProperties> getCircuitBreakerPolicies() {
        return circuitBreakerPolicies;
    }

    /**
     * 设置按名称配置的熔断策略。
     *
     * @param circuitBreakerPolicies 熔断策略映射
     */
    public void setCircuitBreakerPolicies(
            Map<String, CircuitBreakerPolicyProperties> circuitBreakerPolicies) {
        this.circuitBreakerPolicies = circuitBreakerPolicies == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(circuitBreakerPolicies);
    }

    /**
     * 单个令牌桶限流策略配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class RateLimitPolicyProperties {

        private int capacity;
        private int refillTokens;
        private Duration refillPeriod = Duration.ofSeconds(1);
        private RateLimitScope scope = RateLimitScope.GLOBAL;

        /**
         * 创建限流策略配置。
         */
        public RateLimitPolicyProperties() {
        }

        /**
         * 返回令牌桶容量。
         *
         * @return 令牌桶容量
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * 设置令牌桶容量。
         *
         * @param capacity 令牌桶容量
         */
        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 返回每周期补充令牌数。
         *
         * @return 补充令牌数
         */
        public int getRefillTokens() {
            return refillTokens;
        }

        /**
         * 设置每周期补充令牌数。
         *
         * @param refillTokens 补充令牌数
         */
        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        /**
         * 返回令牌补充周期。
         *
         * @return 补充周期
         */
        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        /**
         * 设置令牌补充周期。
         *
         * @param refillPeriod 补充周期
         */
        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        /**
         * 返回限流隔离范围。
         *
         * @return 隔离范围
         */
        public RateLimitScope getScope() {
            return scope;
        }

        /**
         * 设置限流隔离范围。
         *
         * @param scope 隔离范围
         */
        public void setScope(RateLimitScope scope) {
            this.scope = scope;
        }
    }

    /**
     * 单个指数退避重试策略配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class RetryPolicyProperties {

        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(100);
        private double multiplier = 2D;
        private Duration maxDelay = Duration.ofSeconds(1);
        private double jitter = 0.2D;
        private Set<Class<? extends Throwable>> retryOn = new LinkedHashSet<>(Set.of(Exception.class));
        private Set<Class<? extends Throwable>> abortOn = new LinkedHashSet<>();

        /**
         * 创建重试策略配置。
         */
        public RetryPolicyProperties() {
        }

        /**
         * 返回包含首次调用在内的最大调用次数。
         *
         * @return 最大调用次数
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * 设置包含首次调用在内的最大调用次数。
         *
         * @param maxAttempts 最大调用次数
         */
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        /**
         * 返回首次重试等待时间。
         *
         * @return 首次等待时间
         */
        public Duration getInitialDelay() {
            return initialDelay;
        }

        /**
         * 设置首次重试等待时间。
         *
         * @param initialDelay 首次等待时间
         */
        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        /**
         * 返回指数退避倍数。
         *
         * @return 退避倍数
         */
        public double getMultiplier() {
            return multiplier;
        }

        /**
         * 设置指数退避倍数。
         *
         * @param multiplier 退避倍数
         */
        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        /**
         * 返回单次等待时间上限。
         *
         * @return 最大等待时间
         */
        public Duration getMaxDelay() {
            return maxDelay;
        }

        /**
         * 设置单次等待时间上限。
         *
         * @param maxDelay 最大等待时间
         */
        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        /**
         * 返回随机抖动比例。
         *
         * @return 抖动比例
         */
        public double getJitter() {
            return jitter;
        }

        /**
         * 设置随机抖动比例。
         *
         * @param jitter 抖动比例
         */
        public void setJitter(double jitter) {
            this.jitter = jitter;
        }

        /**
         * 返回允许重试的异常类型。
         *
         * @return 允许异常类型
         */
        public Set<Class<? extends Throwable>> getRetryOn() {
            return retryOn;
        }

        /**
         * 设置允许重试的异常类型。
         *
         * @param retryOn 允许异常类型
         */
        public void setRetryOn(Set<Class<? extends Throwable>> retryOn) {
            this.retryOn = retryOn == null ? new LinkedHashSet<>() : new LinkedHashSet<>(retryOn);
        }

        /**
         * 返回禁止重试的异常类型。
         *
         * @return 禁止异常类型
         */
        public Set<Class<? extends Throwable>> getAbortOn() {
            return abortOn;
        }

        /**
         * 设置禁止重试的异常类型。
         *
         * @param abortOn 禁止异常类型
         */
        public void setAbortOn(Set<Class<? extends Throwable>> abortOn) {
            this.abortOn = abortOn == null ? new LinkedHashSet<>() : new LinkedHashSet<>(abortOn);
        }
    }

    /**
     * 单个计数滑动窗口熔断策略配置。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static class CircuitBreakerPolicyProperties {

        private float failureRateThreshold = 50F;
        private float slowCallRateThreshold = 100F;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(5);
        private int minimumNumberOfCalls = 10;
        private int slidingWindowSize = 100;
        private int permittedCallsInHalfOpen = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private boolean automaticTransition = true;
        private Set<Class<? extends Throwable>> failureOn = new LinkedHashSet<>(Set.of(Exception.class));
        private Set<Class<? extends Throwable>> ignoreOn = new LinkedHashSet<>();

        /**
         * 创建熔断策略配置。
         */
        public CircuitBreakerPolicyProperties() {
        }

        /**
         * 返回失败率阈值。
         *
         * @return 失败率百分比
         */
        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        /**
         * 设置失败率阈值。
         *
         * @param failureRateThreshold 失败率百分比
         */
        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        /**
         * 返回慢调用率阈值。
         *
         * @return 慢调用率百分比
         */
        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        /**
         * 设置慢调用率阈值。
         *
         * @param slowCallRateThreshold 慢调用率百分比
         */
        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        /**
         * 返回慢调用耗时阈值。
         *
         * @return 慢调用耗时阈值
         */
        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        /**
         * 设置慢调用耗时阈值。
         *
         * @param slowCallDurationThreshold 慢调用耗时阈值
         */
        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        /**
         * 返回计算阈值前的最少调用数。
         *
         * @return 最少调用数
         */
        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        /**
         * 设置计算阈值前的最少调用数。
         *
         * @param minimumNumberOfCalls 最少调用数
         */
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        /**
         * 返回计数滑动窗口大小。
         *
         * @return 窗口大小
         */
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        /**
         * 设置计数滑动窗口大小。
         *
         * @param slidingWindowSize 窗口大小
         */
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        /**
         * 返回半开状态探测调用数。
         *
         * @return 探测调用数
         */
        public int getPermittedCallsInHalfOpen() {
            return permittedCallsInHalfOpen;
        }

        /**
         * 设置半开状态探测调用数。
         *
         * @param permittedCallsInHalfOpen 探测调用数
         */
        public void setPermittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
        }

        /**
         * 返回开启状态等待时间。
         *
         * @return 开启等待时间
         */
        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        /**
         * 设置开启状态等待时间。
         *
         * @param waitDurationInOpenState 开启等待时间
         */
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        /**
         * 返回是否自动进入半开状态。
         *
         * @return 是否自动转换
         */
        public boolean isAutomaticTransition() {
            return automaticTransition;
        }

        /**
         * 设置是否自动进入半开状态。
         *
         * @param automaticTransition 是否自动转换
         */
        public void setAutomaticTransition(boolean automaticTransition) {
            this.automaticTransition = automaticTransition;
        }

        /**
         * 返回计入失败率的异常类型。
         *
         * @return 失败异常类型
         */
        public Set<Class<? extends Throwable>> getFailureOn() {
            return failureOn;
        }

        /**
         * 设置计入失败率的异常类型。
         *
         * @param failureOn 失败异常类型
         */
        public void setFailureOn(Set<Class<? extends Throwable>> failureOn) {
            this.failureOn = failureOn == null ? new LinkedHashSet<>() : new LinkedHashSet<>(failureOn);
        }

        /**
         * 返回完全忽略的异常类型。
         *
         * @return 忽略异常类型
         */
        public Set<Class<? extends Throwable>> getIgnoreOn() {
            return ignoreOn;
        }

        /**
         * 设置完全忽略的异常类型。
         *
         * @param ignoreOn 忽略异常类型
         */
        public void setIgnoreOn(Set<Class<? extends Throwable>> ignoreOn) {
            this.ignoreOn = ignoreOn == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ignoreOn);
        }
    }
}
