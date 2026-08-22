package io.github.zhanghslq.muskit.cache.redis;

import io.github.zhanghslq.muskit.cache.model.CacheRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 缓存键隐藏和记录编解码测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RedisCacheStoreTest {

    /**
     * 验证业务键不会直接进入 Redis Key，且记录可以完整往返。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void shouldHashKeyAndRoundTripRecord() {
        RedissonClient client = mock(RedissonClient.class);
        RBucket bucket = mock(RBucket.class);
        AtomicReference<byte[]> encoded = new AtomicReference<>();
        when(client.getBucket(anyString(), any(Codec.class))).thenReturn(bucket);
        doAnswer(invocation -> {
            encoded.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(any(byte[].class), any(Duration.class));
        when(bucket.get()).thenAnswer(invocation -> encoded.get());
        RedisCacheStore store = new RedisCacheStore(client, "muskit:cache");
        CacheRecord record = new CacheRecord(
                new byte[] {1, 2, 3},
                false,
                Instant.parse("2026-08-20T00:00:10Z"),
                Instant.parse("2026-08-20T00:00:20Z"));

        store.put("orders", "sensitive-business-key", record, Duration.ofSeconds(20));

        assertThat(store.get("orders", "sensitive-business-key")).contains(record);
        verify(client, times(2)).getBucket(
                org.mockito.ArgumentMatchers.argThat(key -> !key.contains("sensitive-business-key")),
                any(Codec.class));
    }
}
