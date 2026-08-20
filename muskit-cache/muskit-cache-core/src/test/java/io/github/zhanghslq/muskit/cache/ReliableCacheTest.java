package io.github.zhanghslq.muskit.cache;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 可靠缓存 SingleFlight、空值、旧值刷新和失败语义测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class ReliableCacheTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-20T00:00:00Z");
    private static final CachePolicy POLICY = new CachePolicy(
            "default",
            Duration.ofMinutes(1),
            Duration.ofSeconds(10),
            0D,
            Duration.ofMinutes(1),
            CacheFailureMode.FAIL_FAST);
    private static final CacheCodec<String> STRING_CODEC = new CacheCodec<>() {
        /** {@inheritDoc} */
        @Override
        public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        /** {@inheritDoc} */
        @Override
        public String decode(byte[] payload) {
            return new String(payload, StandardCharsets.UTF_8);
        }
    };

    /**
     * 验证同 JVM 并发未命中只调用一次权威数据源。
     *
     * @throws Exception 并发等待异常
     */
    @Test
    void shouldCoalesceConcurrentMisses() throws Exception {
        InMemoryCacheStore store = new InMemoryCacheStore();
        ReliableCache cache = cacheAt(store, INITIAL_TIME);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> getUnchecked(cache, () -> {
            loads.incrementAndGet();
            loading.countDown();
            release.await();
            return "value";
        }));
        assertThat(loading.await(1, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<String> second = CompletableFuture.supplyAsync(
                () -> getUnchecked(cache, () -> {
                    loads.incrementAndGet();
                    return "other";
                }));
        release.countDown();

        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("value");
        assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("value");
        assertThat(loads).hasValue(1);
    }

    /**
     * 验证空值使用独立短 TTL 并阻止缓存穿透。
     *
     * @throws Exception 加载异常
     */
    @Test
    void shouldCacheNullValue() throws Exception {
        InMemoryCacheStore store = new InMemoryCacheStore();
        ReliableCache cache = cacheAt(store, INITIAL_TIME);
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("orders", "missing", POLICY, STRING_CODEC, () -> {
            loads.incrementAndGet();
            return null;
        })).isNull();
        assertThat(cache.get("orders", "missing", POLICY, STRING_CODEC, () -> {
            loads.incrementAndGet();
            return "unexpected";
        })).isNull();

        assertThat(loads).hasValue(1);
        assertThat(store.values.get("orders:missing").record.nullValue()).isTrue();
    }

    /**
     * 验证旧值立即返回，并由刷新执行器更新后端记录。
     *
     * @throws Exception 加载异常
     */
    @Test
    void shouldServeStaleAndRefresh() throws Exception {
        InMemoryCacheStore store = new InMemoryCacheStore();
        ReliableCache initial = cacheAt(store, INITIAL_TIME);
        assertThat(initial.get("orders", "one", POLICY, STRING_CODEC, () -> "old")).isEqualTo("old");
        ReliableCache stale = cacheAt(store, INITIAL_TIME.plusSeconds(61));

        assertThat(stale.get("orders", "one", POLICY, STRING_CODEC, () -> "new")).isEqualTo("old");
        assertThat(stale.get("orders", "one", POLICY, STRING_CODEC, () -> "unexpected")).isEqualTo("new");
    }

    /**
     * 验证默认后端失败明确抛出，显式绕过模式才允许直读权威数据源。
     *
     * @throws Exception 显式绕过加载异常
     */
    @Test
    void shouldRequireExplicitBackendBypass() throws Exception {
        InMemoryCacheStore store = new InMemoryCacheStore();
        store.failReads = true;
        ReliableCache cache = cacheAt(store, INITIAL_TIME);

        assertThatThrownBy(() -> cache.get("orders", "one", POLICY, STRING_CODEC, () -> "value"))
                .isInstanceOf(CacheBackendException.class);
        CachePolicy bypass = new CachePolicy(
                "bypass", POLICY.ttl(), POLICY.nullTtl(), 0D,
                POLICY.staleWhileRevalidate(), CacheFailureMode.LOAD_WITHOUT_CACHE);
        assertThat(cache.get("orders", "one", bypass, STRING_CODEC, () -> "value"))
                .isEqualTo("value");
    }

    /**
     * 创建使用固定时钟和同步刷新执行器的缓存。
     *
     * @param store 内存存储
     * @param instant 当前时刻
     * @return 可靠缓存
     */
    private ReliableCache cacheAt(InMemoryCacheStore store, Instant instant) {
        return new ReliableCache(
                store,
                Runnable::run,
                io.github.zhanghslq.muskit.observation.MuskitObservationRegistry.noop(),
                Clock.fixed(instant, ZoneOffset.UTC),
                () -> 0.5D);
    }

    /**
     * 将受检异常转换为异步测试异常。
     *
     * @param cache 可靠缓存
     * @param loader 数据加载器
     * @return 缓存结果
     */
    private String getUnchecked(ReliableCache cache, CacheLoader<String> loader) {
        try {
            return cache.get("orders", "one", POLICY, STRING_CODEC, loader);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * 支持故障注入的内存缓存存储测试替身。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class InMemoryCacheStore implements CacheStore {

        private final Map<String, Stored> values = new ConcurrentHashMap<>();
        private volatile boolean failReads;

        /**
         * 创建内存缓存存储。
         */
        private InMemoryCacheStore() {
        }

        /**
         * 读取内存缓存记录。
         *
         * @param cacheName 缓存名称
         * @param key 业务缓存键
         * @return 缓存记录
         */
        @Override
        public Optional<CacheRecord> get(String cacheName, String key) {
            if (failReads) {
                throw new IllegalStateException("redis unavailable");
            }
            Stored stored = values.get(cacheName + ':' + key);
            return Optional.ofNullable(stored == null ? null : stored.record);
        }

        /**
         * 保存内存缓存记录。
         *
         * @param cacheName 缓存名称
         * @param key 业务缓存键
         * @param record 缓存记录
         * @param retention 保留时间
         */
        @Override
        public void put(String cacheName, String key, CacheRecord record, Duration retention) {
            values.put(cacheName + ':' + key, new Stored(record, retention));
        }

        /**
         * 删除内存缓存记录。
         *
         * @param cacheName 缓存名称
         * @param key 业务缓存键
         */
        @Override
        public void delete(String cacheName, String key) {
            values.remove(cacheName + ':' + key);
        }
    }

    /**
     * 保存内存记录和后端保留时间。
     *
     * @param record 缓存记录
     * @param retention 保留时间
     * @author zhs
     * @since 2026-08-20
     */
    private record Stored(CacheRecord record, Duration retention) {
    }
}
