package io.github.zhanghslq.muskit.state.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 状态机配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.state")
public class MuskitStateProperties {

    private boolean enabled = true;
    private int maxConflictRetries = 3;

    /**
     * 返回是否启用状态机工厂。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用状态机工厂。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回乐观锁最大重试次数。
     *
     * @return 最大重试次数
     */
    public int getMaxConflictRetries() {
        return maxConflictRetries;
    }

    /**
     * 设置乐观锁最大重试次数。
     *
     * @param maxConflictRetries 最大重试次数
     */
    public void setMaxConflictRetries(int maxConflictRetries) {
        this.maxConflictRetries = maxConflictRetries;
    }
}
