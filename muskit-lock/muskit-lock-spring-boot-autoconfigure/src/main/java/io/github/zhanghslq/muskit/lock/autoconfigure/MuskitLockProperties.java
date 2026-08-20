package io.github.zhanghslq.muskit.lock.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Muskit Redis 分布式锁配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.lock")
public class MuskitLockProperties {

    private boolean enabled = true;
    private int order = Ordered.HIGHEST_PRECEDENCE + 50;
    private String keyPrefix = "muskit:lock:";

    /**
     * 创建 Muskit 分布式锁配置属性。
     */
    public MuskitLockProperties() {
    }

    /**
     * 返回分布式锁是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置分布式锁是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回锁切面执行顺序。
     *
     * @return 切面顺序
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置锁切面执行顺序。
     *
     * @param order 切面顺序
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 返回 Redis 锁键前缀。
     *
     * @return Redis 锁键前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置 Redis 锁键前缀。
     *
     * @param keyPrefix Redis 锁键前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
