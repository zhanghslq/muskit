package io.github.zhanghslq.muskit.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.DoubleSupplier;

import io.github.zhanghslq.muskit.observation.MuskitMetric;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.ObservationTags;

/**
 * 提供进程内 SingleFlight、空值缓存、TTL 抖动和旧值异步刷新的可靠缓存。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ReliableCache {

    private final CacheStore store;
    private final Executor refreshExecutor;
    private final MuskitObservationRegistry observationRegistry;
    private final Clock clock;
    private final DoubleSupplier random;
    private final ConcurrentMap<FlightKey, CompletableFuture<CacheRecord>> flights = new ConcurrentHashMap<>();

    /**
     * 使用系统时钟、无抖动随机定制和空观测注册器创建可靠缓存。
     *
     * @param store 缓存存储
     * @param refreshExecutor 旧值异步刷新执行器
     */
    public ReliableCache(CacheStore store, Executor refreshExecutor) {
        this(store, refreshExecutor, MuskitObservationRegistry.noop(), Clock.systemUTC(), Math::random);
    }

    /**
     * 创建可测试且带统一可观测性的可靠缓存。
     *
     * @param store 缓存存储
     * @param refreshExecutor 旧值异步刷新执行器
     * @param observationRegistry 统一观测注册器
     * @param clock 缓存时钟
     * @param random 返回零到一之间值的随机源
     */
    public ReliableCache(
            CacheStore store,
            Executor refreshExecutor,
            MuskitObservationRegistry observationRegistry,
            Clock clock,
            DoubleSupplier random) {
        this.store = Objects.requireNonNull(store, "缓存存储不能为空");
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "缓存刷新执行器不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        this.clock = Objects.requireNonNull(clock, "缓存时钟不能为空");
        this.random = Objects.requireNonNull(random, "缓存 TTL 随机源不能为空");
    }

    /**
     * 读取缓存；未命中时合并同 JVM 并发加载，旧值存在时立即返回并触发单次异步刷新。
     *
     * @param cacheName 低基数缓存名称
     * @param key 业务缓存键
     * @param policy 缓存策略
     * @param codec 值编解码器
     * @param loader 权威数据加载器
     * @param <T> 业务值类型
     * @return 缓存值或加载值，可以为空
     * @throws Exception 数据加载异常
     */
    public <T> T get(
            String cacheName,
            String key,
            CachePolicy policy,
            CacheCodec<T> codec,
            CacheLoader<T> loader) throws Exception {
        validateIdentity(cacheName, key);
        Objects.requireNonNull(policy, "缓存策略不能为空");
        Objects.requireNonNull(codec, "缓存编解码器不能为空");
        Objects.requireNonNull(loader, "缓存数据加载器不能为空");
        ObservationTags tags = ObservationTags.of(MuskitTagKey.CACHE, cacheName);
        Optional<CacheRecord> stored;
        try {
            stored = store.get(cacheName, key);
        } catch (RuntimeException backendFailure) {
            if (policy.failureMode() == CacheFailureMode.LOAD_WITHOUT_CACHE) {
                record(tags, "backend-bypass");
                return loader.load();
            }
            throw backend("get", backendFailure);
        }

        Instant now = clock.instant();
        if (stored.isPresent() && stored.get().isAliveAt(now)) {
            CacheRecord record = stored.get();
            if (record.isFreshAt(now)) {
                record(tags, record.nullValue() ? "null-hit" : "hit");
                return decode(record, codec);
            }
            record(tags, "stale-hit");
            scheduleRefresh(cacheName, key, policy, codec, loader, tags);
            return decode(record, codec);
        }

        record(tags, "miss");
        CacheRecord loaded = loadCoalesced(cacheName, key, policy, codec, loader, tags);
        return decode(loaded, codec);
    }

    /**
     * 强一致地删除指定缓存键；删除失败始终向业务暴露，避免旧值继续可见。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     */
    public void invalidate(String cacheName, String key) {
        validateIdentity(cacheName, key);
        try {
            store.delete(cacheName, key);
        } catch (RuntimeException failure) {
            throw backend("delete", failure);
        }
    }

    /**
     * 合并同一缓存键的并发加载请求，等待者共享同一个完成结果。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param policy 缓存策略
     * @param codec 编解码器
     * @param loader 数据加载器
     * @param tags 指标标签
     * @param <T> 业务值类型
     * @return 加载后的缓存记录
     * @throws Exception 加载异常
     */
    private <T> CacheRecord loadCoalesced(
            String cacheName,
            String key,
            CachePolicy policy,
            CacheCodec<T> codec,
            CacheLoader<T> loader,
            ObservationTags tags) throws Exception {
        FlightKey flightKey = new FlightKey(cacheName, key);
        CompletableFuture<CacheRecord> mine = new CompletableFuture<>();
        CompletableFuture<CacheRecord> existing = flights.putIfAbsent(flightKey, mine);
        if (existing != null) {
            record(tags, "singleflight-shared");
            return await(existing);
        }
        try {
            CacheRecord loaded = loadAndStore(cacheName, key, policy, codec, loader, tags);
            mine.complete(loaded);
            return loaded;
        } catch (Throwable failure) {
            mine.completeExceptionally(failure);
            rethrow(failure);
            throw new IllegalStateException("无法到达的缓存异常分支");
        } finally {
            flights.remove(flightKey, mine);
        }
    }

    /**
     * 对旧值启动至多一个后台刷新任务；刷新失败通过指标明确暴露且保留旧值直到最终期限。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param policy 缓存策略
     * @param codec 编解码器
     * @param loader 数据加载器
     * @param tags 指标标签
     * @param <T> 业务值类型
     */
    private <T> void scheduleRefresh(
            String cacheName,
            String key,
            CachePolicy policy,
            CacheCodec<T> codec,
            CacheLoader<T> loader,
            ObservationTags tags) {
        FlightKey flightKey = new FlightKey(cacheName, key);
        CompletableFuture<CacheRecord> refresh = new CompletableFuture<>();
        if (flights.putIfAbsent(flightKey, refresh) != null) {
            return;
        }
        try {
            refreshExecutor.execute(() -> {
                try {
                    CacheRecord loaded = loadAndStore(cacheName, key, policy, codec, loader, tags);
                    refresh.complete(loaded);
                    record(tags, "refresh-completed");
                } catch (Throwable failure) {
                    refresh.completeExceptionally(failure);
                    record(tags, "refresh-failed");
                } finally {
                    flights.remove(flightKey, refresh);
                }
            });
        } catch (RuntimeException rejected) {
            flights.remove(flightKey, refresh);
            refresh.completeExceptionally(rejected);
            record(tags, "refresh-rejected");
        }
    }

    /**
     * 调用权威数据源、编码结果并按有效期写入缓存。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param policy 缓存策略
     * @param codec 编解码器
     * @param loader 数据加载器
     * @param tags 指标标签
     * @param <T> 业务值类型
     * @return 新缓存记录
     * @throws Exception 加载异常
     */
    private <T> CacheRecord loadAndStore(
            String cacheName,
            String key,
            CachePolicy policy,
            CacheCodec<T> codec,
            CacheLoader<T> loader,
            ObservationTags tags) throws Exception {
        T value = loader.load();
        byte[] payload;
        if (value == null) {
            payload = new byte[0];
        } else {
            try {
                payload = Objects.requireNonNull(codec.encode(value), "缓存编码结果不能为空");
            } catch (Exception encodeFailure) {
                throw new CacheCodecException("encode", encodeFailure);
            }
        }
        Duration baseTtl = value == null ? policy.nullTtl() : policy.ttl();
        Duration freshTtl = jitter(baseTtl, policy.ttlJitterRatio());
        Duration retention = freshTtl.plus(policy.staleWhileRevalidate());
        Instant now = clock.instant();
        CacheRecord record = new CacheRecord(
                payload,
                value == null,
                now.plus(freshTtl),
                now.plus(retention));
        try {
            store.put(cacheName, key, record, retention);
        } catch (RuntimeException backendFailure) {
            if (policy.failureMode() == CacheFailureMode.FAIL_FAST) {
                throw backend("put", backendFailure);
            }
            record(tags, "write-bypassed");
        }
        return record;
    }

    /**
     * 解码非空缓存记录，空值占位直接返回空。
     *
     * @param record 缓存记录
     * @param codec 编解码器
     * @param <T> 业务值类型
     * @return 解码结果
     */
    private <T> T decode(CacheRecord record, CacheCodec<T> codec) {
        if (record.nullValue()) {
            return null;
        }
        try {
            return codec.decode(record.payload());
        } catch (Exception decodeFailure) {
            throw new CacheCodecException("decode", decodeFailure);
        }
    }

    /**
     * 计算对称 TTL 抖动，并校验随机源返回范围。
     *
     * @param ttl 基础 TTL
     * @param ratio 抖动比例
     * @return 抖动后的 TTL
     */
    private Duration jitter(Duration ttl, double ratio) {
        if (ratio == 0D) {
            return ttl;
        }
        double randomValue = random.getAsDouble();
        if (!Double.isFinite(randomValue) || randomValue < 0D || randomValue > 1D) {
            throw new IllegalStateException("缓存 TTL 随机源必须返回 [0, 1] 范围内的有限数");
        }
        long nanos = saturatedNanos(ttl);
        double factor = 1D - ratio + 2D * ratio * randomValue;
        return Duration.ofNanos(Math.max(1L, (long) Math.min(Long.MAX_VALUE, nanos * factor)));
    }

    /**
     * 等待共享加载结果并恢复中断标记。
     *
     * @param future 共享结果
     * @return 缓存记录
     * @throws Exception 加载异常
     */
    private CacheRecord await(CompletableFuture<CacheRecord> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (ExecutionException failure) {
            rethrow(failure.getCause());
            throw new IllegalStateException("无法到达的缓存异常分支");
        }
    }

    /**
     * 保持原异常类型重新抛出。
     *
     * @param failure 原异常
     * @throws Exception 受检异常
     */
    private void rethrow(Throwable failure) throws Exception {
        Throwable current = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (current instanceof Exception exception) {
            throw exception;
        }
        if (current instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(current);
    }

    /**
     * 统一包装不包含业务键的缓存后端异常。
     *
     * @param operation 后端操作
     * @param failure 原异常
     * @return 缓存后端异常
     */
    private CacheBackendException backend(String operation, RuntimeException failure) {
        return failure instanceof CacheBackendException cacheFailure
                ? cacheFailure
                : new CacheBackendException(operation, failure);
    }

    /**
     * 校验缓存名称和业务键。
     *
     * @param cacheName 缓存名称
     * @param key 业务键
     */
    private void validateIdentity(String cacheName, String key) {
        if (cacheName == null || cacheName.isBlank() || cacheName.length() > 128) {
            throw new IllegalArgumentException("缓存名称不能为空且长度不能超过 128");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("业务缓存键不能为空");
        }
    }

    /**
     * 将时间转换为饱和纳秒值。
     *
     * @param duration 时间值
     * @return 纳秒值
     */
    private long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 记录低基数缓存查找结果。
     *
     * @param tags 缓存标签
     * @param outcome 查找结果
     */
    private void record(ObservationTags tags, String outcome) {
        observationRegistry.increment(
                MuskitMetric.CACHE_LOOKUP,
                tags.and(MuskitTagKey.OUTCOME, outcome));
    }

    /**
     * SingleFlight 内部键，安全描述不包含业务缓存键。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class FlightKey {

        private final String cacheName;
        private final String key;

        /**
         * 创建 SingleFlight 内部键。
         *
         * @param cacheName 缓存名称
         * @param key 业务键
         */
        private FlightKey(String cacheName, String key) {
            this.cacheName = cacheName;
            this.key = key;
        }

        /**
         * 按缓存名称和业务键判断相等。
         *
         * @param other 待比较对象
         * @return 是否相等
         */
        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof FlightKey that
                    && cacheName.equals(that.cacheName) && key.equals(that.key);
        }

        /**
         * 返回内部键哈希。
         *
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(cacheName, key);
        }

        /**
         * 返回不包含业务键的安全描述。
         *
         * @return 安全描述
         */
        @Override
        public String toString() {
            return "FlightKey[cache=" + cacheName + ']';
        }
    }
}
