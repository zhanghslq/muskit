package io.github.zhanghslq.muskit.cache;

/**
 * 允许返回空值和抛出受检异常的缓存数据加载器。
 *
 * @param <T> 业务值类型
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface CacheLoader<T> {

    /**
     * 从权威数据源加载业务值。
     *
     * @return 业务值，可以为空
     * @throws Exception 加载失败
     */
    T load() throws Exception;
}
