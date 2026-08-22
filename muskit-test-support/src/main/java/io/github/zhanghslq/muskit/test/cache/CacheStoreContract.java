package io.github.zhanghslq.muskit.test.cache;

import io.github.zhanghslq.muskit.cache.model.CacheRecord;
import io.github.zhanghslq.muskit.cache.spi.CacheStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有共享缓存存储 Provider 都应通过的读写契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class CacheStoreContract {

    /**
     * 创建缓存存储契约测试基类。
     */
    protected CacheStoreContract() {
    }

    /**
     * 返回代表第一个应用实例的缓存存储。
     *
     * @return 第一个缓存存储
     */
    protected abstract CacheStore firstStore();

    /**
     * 返回代表第二个应用实例的缓存存储。
     *
     * @return 第二个缓存存储
     */
    protected abstract CacheStore secondStore();

    /**
     * 验证普通记录可以在不同应用实例之间完整读取。
     */
    @Test
    protected final void shouldRoundTripRecordAcrossInstances() {
        String key = uniqueKey();
        CacheRecord record = record(new byte[] {1, 2, 3}, false);

        firstStore().put("provider-contract", key, record, Duration.ofMinutes(1));

        assertEquals(record, secondStore().get("provider-contract", key).orElseThrow());
        secondStore().delete("provider-contract", key);
    }

    /**
     * 验证空值占位记录不会被误判为缓存未命中。
     */
    @Test
    protected final void shouldPreserveNullValueMarker() {
        String key = uniqueKey();
        CacheRecord record = record(new byte[0], true);

        firstStore().put("provider-contract", key, record, Duration.ofMinutes(1));

        CacheRecord loaded = secondStore().get("provider-contract", key).orElseThrow();
        assertTrue(loaded.nullValue());
        assertEquals(record, loaded);
        secondStore().delete("provider-contract", key);
    }

    /**
     * 验证缓存名称和业务键共同参与隔离，删除不会影响其他记录。
     */
    @Test
    protected final void shouldIsolateAndDeleteRecords() {
        String key = uniqueKey();
        CacheRecord first = record(new byte[] {1}, false);
        CacheRecord second = record(new byte[] {2}, false);
        firstStore().put("provider-contract-a", key, first, Duration.ofMinutes(1));
        firstStore().put("provider-contract-b", key, second, Duration.ofMinutes(1));

        secondStore().delete("provider-contract-a", key);

        assertTrue(firstStore().get("provider-contract-a", key).isEmpty());
        assertEquals(second, firstStore().get("provider-contract-b", key).orElseThrow());
        firstStore().delete("provider-contract-b", key);
    }

    /**
     * 创建固定时间边界的缓存记录。
     *
     * @param payload 缓存载荷
     * @param nullValue 是否为空值占位
     * @return 缓存记录
     */
    private CacheRecord record(byte[] payload, boolean nullValue) {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        return new CacheRecord(payload, nullValue, now.plusSeconds(30), now.plusSeconds(60));
    }

    /**
     * 创建避免契约测试互相污染的随机业务键。
     *
     * @return 随机业务键
     */
    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }
}
