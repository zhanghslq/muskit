package io.github.zhanghslq.muskit.example;

import java.nio.charset.StandardCharsets;

import io.github.zhanghslq.muskit.cache.CacheCodec;
import io.github.zhanghslq.muskit.cache.CacheTemplate;
import org.springframework.stereotype.Service;

/**
 * 演示通过策略名称访问可靠 Redis 缓存。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class ProductCacheService {

    private final CacheTemplate cacheTemplate;

    /**
     * 创建商品缓存示例服务。
     *
     * @param cacheTemplate 可靠缓存模板
     */
    public ProductCacheService(CacheTemplate cacheTemplate) {
        this.cacheTemplate = cacheTemplate;
    }

    /**
     * 查询商品名称，缓存未命中时执行加载动作。
     *
     * @param productId 商品标识
     * @return 商品名称
     * @throws Exception 缓存编解码或加载失败
     */
    public String findName(String productId) throws Exception {
        return cacheTemplate.get(
                "products",
                productId,
                "product",
                StringCacheCodec.INSTANCE,
                () -> "product:" + productId);
    }

    /**
     * UTF-8 字符串缓存编解码器。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private enum StringCacheCodec implements CacheCodec<String> {

        /** 共享编解码器实例。 */
        INSTANCE;

        /**
         * 编码字符串。
         *
         * @param value 字符串值
         * @return UTF-8 字节
         */
        @Override
        public byte[] encode(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        /**
         * 解码字符串。
         *
         * @param payload UTF-8 字节
         * @return 字符串值
         */
        @Override
        public String decode(byte[] payload) {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }
}
