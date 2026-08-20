package io.github.zhanghslq.muskit.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 不可变的业务上下文，不负责保存链路追踪系统自身管理的 traceId。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitContext {

    private static final MuskitContext EMPTY = new MuskitContext(Map.of());

    private final Map<String, String> values;

    /**
     * 使用指定键值创建业务上下文。
     *
     * @param values 上下文键值
     */
    private MuskitContext(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /**
     * 返回空业务上下文。
     *
     * @return 空业务上下文
     */
    public static MuskitContext empty() {
        return EMPTY;
    }

    /**
     * 根据给定键值创建业务上下文。
     *
     * @param values 上下文键值
     * @return 不可变业务上下文
     */
    public static MuskitContext of(Map<String, String> values) {
        Objects.requireNonNull(values, "上下文键值不能为空");
        if (values.isEmpty()) {
            return empty();
        }
        values.forEach(MuskitContext::validateEntry);
        return new MuskitContext(values);
    }

    /**
     * 获取指定上下文值。
     *
     * @param key 上下文键
     * @return 上下文值，不存在时返回空
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * 判断是否包含指定上下文键。
     *
     * @param key 上下文键
     * @return 是否包含
     */
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    /**
     * 返回不可变的上下文键值视图。
     *
     * @return 上下文键值
     */
    public Map<String, String> values() {
        return values;
    }

    /**
     * 返回包含新增或替换键值的新上下文。
     *
     * @param key 上下文键
     * @param value 上下文值
     * @return 新业务上下文
     */
    public MuskitContext with(String key, String value) {
        validateEntry(key, value);
        Map<String, String> newValues = new HashMap<>(values);
        newValues.put(key, value);
        return new MuskitContext(newValues);
    }

    /**
     * 返回移除指定键后的新上下文。
     *
     * @param key 上下文键
     * @return 新业务上下文
     */
    public MuskitContext without(String key) {
        if (!values.containsKey(key)) {
            return this;
        }
        Map<String, String> newValues = new HashMap<>(values);
        newValues.remove(key);
        return newValues.isEmpty() ? empty() : new MuskitContext(newValues);
    }

    /**
     * 判断当前上下文是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * 比较两个业务上下文的键值是否相同。
     *
     * @param object 待比较对象
     * @return 是否相同
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MuskitContext that)) {
            return false;
        }
        return values.equals(that.values);
    }

    /**
     * 返回业务上下文的哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * 返回仅包含键名的描述，避免上下文敏感值意外进入日志。
     *
     * @return 上下文描述
     */
    @Override
    public String toString() {
        Set<String> keys = values.keySet();
        return "MuskitContext{keys=" + keys + '}';
    }

    /**
     * 校验单个上下文键值。
     *
     * @param key 上下文键
     * @param value 上下文值
     */
    private static void validateEntry(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("上下文键不能为空");
        }
        Objects.requireNonNull(value, "上下文值不能为空");
    }
}

