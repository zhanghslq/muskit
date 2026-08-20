package io.github.zhanghslq.muskit.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存记录的技术无关存储 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface CacheStore {

    /**
     * 读取缓存记录。
     *
     * @param cacheName 低基数缓存名称
     * @param key 业务缓存键
     * @return 缓存记录
     */
    Optional<CacheRecord> get(String cacheName, String key);

    /**
     * 保存缓存记录并设置后端保留时间。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     * @param record 缓存记录
     * @param retention 后端保留时间
     */
    void put(String cacheName, String key, CacheRecord record, Duration retention);

    /**
     * 删除缓存记录。
     *
     * @param cacheName 缓存名称
     * @param key 业务缓存键
     */
    void delete(String cacheName, String key);
}
