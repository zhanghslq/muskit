package io.github.zhanghslq.muskit.observation.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 只接受预定义低基数键的不可变指标标签。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ObservationTags {

    private static final int MAX_VALUE_LENGTH = 128;
    private static final ObservationTags EMPTY = new ObservationTags(Map.of());

    private final Map<MuskitTagKey, String> values;

    /**
     * 创建不可变指标标签。
     *
     * @param values 标签值
     */
    private ObservationTags(Map<MuskitTagKey, String> values) {
        EnumMap<MuskitTagKey, String> copied = new EnumMap<>(MuskitTagKey.class);
        copied.putAll(values);
        this.values = Collections.unmodifiableMap(copied);
    }

    /**
     * 返回空标签。
     *
     * @return 空标签
     */
    public static ObservationTags empty() {
        return EMPTY;
    }

    /**
     * 创建单个低基数标签。
     *
     * @param key 标签键
     * @param value 标签值
     * @return 指标标签
     */
    public static ObservationTags of(MuskitTagKey key, String value) {
        return EMPTY.and(key, value);
    }

    /**
     * 返回增加或替换指定键后的新标签集合。
     *
     * @param key 标签键
     * @param value 标签值
     * @return 新标签集合
     */
    public ObservationTags and(MuskitTagKey key, String value) {
        Objects.requireNonNull(key, "指标标签键不能为空");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("指标标签值不能为空");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("指标标签值长度不能超过 " + MAX_VALUE_LENGTH);
        }
        EnumMap<MuskitTagKey, String> copied = new EnumMap<>(MuskitTagKey.class);
        copied.putAll(values);
        copied.put(key, value);
        return new ObservationTags(copied);
    }

    /**
     * 返回不可变标签映射。
     *
     * @return 标签映射
     */
    public Map<MuskitTagKey, String> asMap() {
        return values;
    }

    /**
     * 按标签键值判断相等。
     *
     * @param other 待比较对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ObservationTags that && values.equals(that.values);
    }

    /**
     * 返回标签键值哈希。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * 返回低基数标签描述。
     *
     * @return 标签描述
     */
    @Override
    public String toString() {
        return "ObservationTags" + values;
    }
}
