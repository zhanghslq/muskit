package io.github.zhanghslq.muskit.cache;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * 包含空值标记、新鲜期限和最终失效期限的不可变缓存记录。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class CacheRecord {

    private final byte[] payload;
    private final boolean nullValue;
    private final Instant freshUntil;
    private final Instant expiresAt;

    /**
     * 创建缓存记录并复制载荷。
     *
     * @param payload 编码载荷，空值记录使用空数组
     * @param nullValue 是否为空值占位
     * @param freshUntil 新鲜期限
     * @param expiresAt 最终失效期限
     */
    public CacheRecord(byte[] payload, boolean nullValue, Instant freshUntil, Instant expiresAt) {
        this.payload = Objects.requireNonNull(payload, "缓存载荷不能为空").clone();
        this.nullValue = nullValue;
        this.freshUntil = Objects.requireNonNull(freshUntil, "缓存新鲜期限不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "缓存最终失效期限不能为空");
        if (expiresAt.isBefore(freshUntil)) {
            throw new IllegalArgumentException("缓存最终失效期限不能早于新鲜期限");
        }
        if (nullValue && payload.length != 0) {
            throw new IllegalArgumentException("缓存空值记录不能包含载荷");
        }
    }

    /**
     * 返回载荷副本。
     *
     * @return 编码载荷
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * 返回是否为空值占位。
     *
     * @return 是否为空值
     */
    public boolean nullValue() {
        return nullValue;
    }

    /**
     * 返回新鲜期限。
     *
     * @return 新鲜期限
     */
    public Instant freshUntil() {
        return freshUntil;
    }

    /**
     * 返回最终失效期限。
     *
     * @return 最终失效期限
     */
    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * 判断指定时刻记录是否仍然新鲜。
     *
     * @param now 当前时刻
     * @return 是否新鲜
     */
    public boolean isFreshAt(Instant now) {
        return now.isBefore(freshUntil);
    }

    /**
     * 判断指定时刻记录是否仍可作为旧值返回。
     *
     * @param now 当前时刻
     * @return 是否仍有效
     */
    public boolean isAliveAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    /**
     * 按记录内容判断相等。
     *
     * @param other 待比较对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CacheRecord that
                && nullValue == that.nullValue
                && Arrays.equals(payload, that.payload)
                && freshUntil.equals(that.freshUntil)
                && expiresAt.equals(that.expiresAt);
    }

    /**
     * 返回记录内容哈希。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(payload), nullValue, freshUntil, expiresAt);
    }

    /**
     * 返回不包含缓存载荷的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "CacheRecord[nullValue=" + nullValue + ", payloadBytes=" + payload.length + ']';
    }
}
