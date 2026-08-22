package io.github.zhanghslq.muskit.cache.spi;

/**
 * 业务缓存值与字节载荷之间的可替换编码 SPI。
 *
 * @param <T> 业务值类型
 * @author zhs
 * @since 2026-08-20
 */
public interface CacheCodec<T> {

    /**
     * 编码非空业务值。
     *
     * @param value 业务值
     * @return 编码载荷
     * @throws Exception 编码失败
     */
    byte[] encode(T value) throws Exception;

    /**
     * 解码业务值。
     *
     * @param payload 编码载荷
     * @return 业务值
     * @throws Exception 解码失败
     */
    T decode(byte[] payload) throws Exception;
}
