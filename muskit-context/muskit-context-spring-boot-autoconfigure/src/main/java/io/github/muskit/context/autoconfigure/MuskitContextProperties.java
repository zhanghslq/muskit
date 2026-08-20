package io.github.muskit.context.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 业务上下文自动配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.context")
public class MuskitContextProperties {

    private boolean enabled = true;
    private boolean taskDecoratorEnabled = true;

    /**
     * 创建 Muskit 业务上下文配置属性。
     */
    public MuskitContextProperties() {
    }

    /**
     * 返回业务上下文传播是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置业务上下文传播是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Spring 任务装饰器是否启用。
     *
     * @return 是否启用任务装饰器
     */
    public boolean isTaskDecoratorEnabled() {
        return taskDecoratorEnabled;
    }

    /**
     * 设置 Spring 任务装饰器是否启用。
     *
     * @param taskDecoratorEnabled 是否启用任务装饰器
     */
    public void setTaskDecoratorEnabled(boolean taskDecoratorEnabled) {
        this.taskDecoratorEnabled = taskDecoratorEnabled;
    }
}
