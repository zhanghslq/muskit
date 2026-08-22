package io.github.zhanghslq.muskit.cache.spi;

import io.github.zhanghslq.muskit.cache.model.CachePolicy;

/**
 * 按低基数名称解析缓存策略的可替换 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface CachePolicyResolver {

    /**
     * 解析指定缓存策略。
     *
     * @param policyName 策略名称
     * @return 缓存策略
     */
    CachePolicy resolve(String policyName);
}
