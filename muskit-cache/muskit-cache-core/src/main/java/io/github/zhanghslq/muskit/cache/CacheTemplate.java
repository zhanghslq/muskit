package io.github.zhanghslq.muskit.cache;

import java.util.Objects;

/**
 * 使用策略名称访问可靠缓存的业务入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class CacheTemplate {

    private final ReliableCache cache;
    private final CachePolicyResolver policyResolver;

    /**
     * 创建缓存业务入口。
     *
     * @param cache 可靠缓存
     * @param policyResolver 策略解析器
     */
    public CacheTemplate(ReliableCache cache, CachePolicyResolver policyResolver) {
        this.cache = Objects.requireNonNull(cache, "可靠缓存不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "缓存策略解析器不能为空");
    }

    /**
     * 按策略名称获取缓存值。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param policyName 策略名称
     * @param codec 编解码器
     * @param loader 数据加载器
     * @param <T> 业务值类型
     * @return 缓存值或加载值
     * @throws Exception 数据加载异常
     */
    public <T> T get(
            String cacheName,
            String key,
            String policyName,
            CacheCodec<T> codec,
            CacheLoader<T> loader) throws Exception {
        return cache.get(cacheName, key, policyResolver.resolve(policyName), codec, loader);
    }

    /**
     * 删除指定缓存键。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     */
    public void invalidate(String cacheName, String key) {
        cache.invalidate(cacheName, key);
    }
}
