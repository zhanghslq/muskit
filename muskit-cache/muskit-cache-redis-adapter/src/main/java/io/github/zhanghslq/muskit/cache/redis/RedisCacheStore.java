package io.github.zhanghslq.muskit.cache.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import io.github.zhanghslq.muskit.cache.CacheBackendException;
import io.github.zhanghslq.muskit.cache.CacheRecord;
import io.github.zhanghslq.muskit.cache.CacheStore;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;

/**
 * 使用 SHA-256 隐藏业务键并以有界二进制格式存储记录的 Redis 缓存 Provider。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisCacheStore implements CacheStore {

    private static final int FORMAT_VERSION = 1;
    private static final int FIXED_BYTES = Integer.BYTES + 1
            + Long.BYTES + Integer.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final Pattern CACHE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final RedissonClient redissonClient;
    private final String keyPrefix;

    /**
     * 创建 Redis 缓存存储。
     *
     * @param redissonClient Redisson 客户端
     * @param keyPrefix Redis Key 前缀
     */
    public RedisCacheStore(RedissonClient redissonClient, String keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "RedissonClient 不能为空");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 缓存 Key 前缀不能为空");
        }
        this.keyPrefix = keyPrefix;
    }

    /**
     * 读取并解码 Redis 缓存记录。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @return 缓存记录
     */
    @Override
    public Optional<CacheRecord> get(String cacheName, String key) {
        try {
            byte[] encoded = bucket(cacheName, key).get();
            return encoded == null ? Optional.empty() : Optional.of(decode(encoded));
        } catch (CacheBackendException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CacheBackendException("redis-get", failure);
        }
    }

    /**
     * 编码记录并按最终保留时间写入 Redis。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param record 缓存记录
     * @param retention 后端保留时间
     */
    @Override
    public void put(String cacheName, String key, CacheRecord record, Duration retention) {
        Objects.requireNonNull(record, "缓存记录不能为空");
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("Redis 缓存保留时间必须大于 0");
        }
        try {
            bucket(cacheName, key).set(encode(record), retention);
        } catch (RuntimeException failure) {
            throw new CacheBackendException("redis-put", failure);
        }
    }

    /**
     * 删除 Redis 缓存记录。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     */
    @Override
    public void delete(String cacheName, String key) {
        try {
            bucket(cacheName, key).delete();
        } catch (RuntimeException failure) {
            throw new CacheBackendException("redis-delete", failure);
        }
    }

    /**
     * 返回使用原始字节 Codec 的 Redis Bucket。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @return Redis Bucket
     */
    private RBucket<byte[]> bucket(String cacheName, String key) {
        validateIdentity(cacheName, key);
        return redissonClient.getBucket(redisKey(cacheName, key), ByteArrayCodec.INSTANCE);
    }

    /**
     * 把业务键哈希为固定长度 Redis Key，避免键内容出现在 Redis 监控和异常中。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @return Redis Key
     */
    private String redisKey(String cacheName, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return keyPrefix + ':' + cacheName + ':' + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", impossible);
        }
    }

    /**
     * 以带版本和长度的二进制格式编码缓存记录。
     *
     * @param record 缓存记录
     * @return 编码字节
     */
    private byte[] encode(CacheRecord record) {
        byte[] payload = record.payload();
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("缓存载荷不能超过 64 MiB");
        }
        ByteBuffer buffer = ByteBuffer.allocate(FIXED_BYTES + payload.length);
        buffer.putInt(FORMAT_VERSION);
        buffer.put((byte) (record.nullValue() ? 1 : 0));
        putInstant(buffer, record.freshUntil());
        putInstant(buffer, record.expiresAt());
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    /**
     * 解码并严格校验 Redis 缓存记录。
     *
     * @param encoded 编码字节
     * @return 缓存记录
     */
    private CacheRecord decode(byte[] encoded) {
        if (encoded.length < FIXED_BYTES) {
            throw new CacheBackendException("redis-decode", new IllegalArgumentException("缓存记录不完整"));
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            if (buffer.getInt() != FORMAT_VERSION) {
                throw new IllegalArgumentException("缓存记录版本不受支持");
            }
            boolean nullValue = buffer.get() == 1;
            Instant freshUntil = getInstant(buffer);
            Instant expiresAt = getInstant(buffer);
            int payloadLength = buffer.getInt();
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES || payloadLength != buffer.remaining()) {
                throw new IllegalArgumentException("缓存载荷长度无效");
            }
            byte[] payload = new byte[payloadLength];
            buffer.get(payload);
            return new CacheRecord(payload, nullValue, freshUntil, expiresAt);
        } catch (RuntimeException failure) {
            throw new CacheBackendException("redis-decode", failure);
        }
    }

    /**
     * 写入秒和纳秒组成的时刻。
     *
     * @param buffer 二进制缓冲区
     * @param instant 时刻
     */
    private void putInstant(ByteBuffer buffer, Instant instant) {
        buffer.putLong(instant.getEpochSecond());
        buffer.putInt(instant.getNano());
    }

    /**
     * 读取秒和纳秒组成的时刻。
     *
     * @param buffer 二进制缓冲区
     * @return 时刻
     */
    private Instant getInstant(ByteBuffer buffer) {
        return Instant.ofEpochSecond(buffer.getLong(), buffer.getInt());
    }

    /**
     * 校验缓存名称和业务键。
     *
     * @param cacheName 缓存名称
     * @param key 业务键
     */
    private void validateIdentity(String cacheName, String key) {
        if (cacheName == null || !CACHE_NAME_PATTERN.matcher(cacheName).matches()) {
            throw new IllegalArgumentException("Redis 缓存名称只能包含字母、数字、点、下划线和连字符");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis 业务缓存键不能为空");
        }
    }
}
