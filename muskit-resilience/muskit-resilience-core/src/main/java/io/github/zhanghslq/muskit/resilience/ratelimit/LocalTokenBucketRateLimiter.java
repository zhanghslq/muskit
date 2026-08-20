package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 使用单调时钟实现的线程安全本地令牌桶限流器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class LocalTokenBucketRateLimiter implements RateLimiter {

    private static final int DEFAULT_MAX_BUCKETS = 100_000;
    private static final Duration DEFAULT_IDLE_RETENTION = Duration.ofMinutes(10);

    private final ConcurrentMap<BucketKey, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxBuckets;
    private final long idleRetentionNanos;
    private final LongSupplier ticker;
    private final AtomicLong accessCount = new AtomicLong();

    /**
     * 使用默认桶数量和空闲清理时间创建本地限流器。
     */
    public LocalTokenBucketRateLimiter() {
        this(DEFAULT_MAX_BUCKETS, DEFAULT_IDLE_RETENTION, System::nanoTime);
    }

    /**
     * 使用指定容量保护参数创建本地限流器。
     *
     * @param maxBuckets 最大本地令牌桶数量
     * @param idleRetention 空闲桶保留时间
     */
    public LocalTokenBucketRateLimiter(int maxBuckets, Duration idleRetention) {
        this(maxBuckets, idleRetention, System::nanoTime);
    }

    /**
     * 使用可控单调时钟创建本地限流器。
     *
     * @param maxBuckets 最大本地令牌桶数量
     * @param idleRetention 空闲桶保留时间
     * @param ticker 单调纳秒时钟
     */
    LocalTokenBucketRateLimiter(int maxBuckets, Duration idleRetention, LongSupplier ticker) {
        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("最大限流桶数量必须大于 0");
        }
        Objects.requireNonNull(idleRetention, "限流桶空闲保留时间不能为空");
        if (idleRetention.isZero() || idleRetention.isNegative()) {
            throw new IllegalArgumentException("限流桶空闲保留时间必须大于 0");
        }
        this.maxBuckets = maxBuckets;
        this.idleRetentionNanos = idleRetention.toNanos();
        this.ticker = Objects.requireNonNull(ticker, "单调时钟不能为空");
    }

    /**
     * 从对应令牌桶原子消耗一个令牌。
     *
     * @param request 限流请求
     * @return 限流判定
     */
    @Override
    public RateLimitDecision tryAcquire(RateLimitRequest request) {
        Objects.requireNonNull(request, "限流请求不能为空");
        long now = ticker.getAsLong();
        if ((accessCount.incrementAndGet() & 255) == 0) {
            cleanupIdleBuckets(now);
        }
        BucketKey bucketKey = new BucketKey(request.policy().name(), request.effectiveKey());
        Bucket bucket = buckets.get(bucketKey);
        if (bucket == null) {
            bucket = createBucket(bucketKey, request.policy(), now);
        }
        return bucket.tryAcquire(request.policy(), now);
    }

    /**
     * 在同步区内创建新桶并执行硬容量保护。
     *
     * @param key 桶索引
     * @param policy 限流策略
     * @param now 当前单调时间
     * @return 新建或竞争线程已创建的桶
     */
    private Bucket createBucket(BucketKey key, RateLimitPolicy policy, long now) {
        synchronized (buckets) {
            Bucket existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }
            if (buckets.size() >= maxBuckets) {
                cleanupIdleBuckets(now);
            }
            if (buckets.size() >= maxBuckets) {
                throw new RateLimitBucketCapacityException(policy.name());
            }
            Bucket created = new Bucket(policy, now);
            buckets.put(key, created);
            return created;
        }
    }

    /**
     * 清理长期未访问的令牌桶，限制按业务键隔离时的内存占用。
     *
     * @param now 当前单调时间
     */
    private void cleanupIdleBuckets(long now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isIdle(now, idleRetentionNanos));
    }

    /**
     * 令牌桶内部索引，不提供可能暴露业务键的字符串表示。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class BucketKey {

        private final String policyName;
        private final String key;

        /**
         * 创建令牌桶内部索引。
         *
         * @param policyName 策略名称
         * @param key 业务隔离键
         */
        private BucketKey(String policyName, String key) {
            this.policyName = policyName;
            this.key = key;
        }

        /**
         * 比较两个内部索引。
         *
         * @param other 待比较对象
         * @return 是否相等
         */
        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof BucketKey that
                    && policyName.equals(that.policyName)
                    && key.equals(that.key);
        }

        /**
         * 计算内部索引哈希值。
         *
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            return 31 * policyName.hashCode() + key.hashCode();
        }
    }

    /**
     * 单个线程安全令牌桶。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;
        private long lastAccessNanos;

        /**
         * 使用满容量创建令牌桶。
         *
         * @param policy 限流策略
         * @param now 当前单调时间
         */
        private Bucket(RateLimitPolicy policy, long now) {
            this.tokens = policy.capacity();
            this.lastRefillNanos = now;
            this.lastAccessNanos = now;
        }

        /**
         * 补充并尝试消耗一个令牌。
         *
         * @param policy 当前限流策略
         * @param now 当前单调时间
         * @return 限流判定
         */
        private synchronized RateLimitDecision tryAcquire(RateLimitPolicy policy, long now) {
            long elapsed = Math.max(0, now - lastRefillNanos);
            double refillRate = (double) policy.refillTokens() / policy.refillPeriod().toNanos();
            tokens = Math.min(policy.capacity(), tokens + elapsed * refillRate);
            lastRefillNanos = now;
            lastAccessNanos = now;
            if (tokens >= 1D) {
                tokens -= 1D;
                return RateLimitDecision.permitted();
            }
            long retryNanos = Math.max(1L, (long) Math.ceil((1D - tokens) / refillRate));
            return RateLimitDecision.rejected(Duration.ofNanos(retryNanos));
        }

        /**
         * 判断令牌桶是否超过空闲保留时间。
         *
         * @param now 当前单调时间
         * @param idleRetentionNanos 空闲保留纳秒数
         * @return 是否长期空闲
         */
        private synchronized boolean isIdle(long now, long idleRetentionNanos) {
            return now - lastAccessNanos >= idleRetentionNanos;
        }
    }
}
