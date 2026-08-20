package io.github.muskit.context.autoconfigure;

import io.micrometer.context.ContextRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * 负责在应用生命周期内注册和注销 Muskit 上下文访问器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitContextAccessorRegistrar implements InitializingBean, DisposableBean {

    private static final Object REGISTRATION_MONITOR = new Object();
    private static int registrationCount;

    private final ContextRegistry contextRegistry;
    private final MuskitContextThreadLocalAccessor accessor;
    private boolean registered;

    /**
     * 创建 Muskit 上下文访问器注册器。
     *
     * @param contextRegistry Micrometer 上下文注册表
     * @param accessor Muskit 上下文访问器
     */
    public MuskitContextAccessorRegistrar(
            ContextRegistry contextRegistry,
            MuskitContextThreadLocalAccessor accessor) {
        this.contextRegistry = contextRegistry;
        this.accessor = accessor;
    }

    /**
     * 在 Spring Bean 初始化后注册上下文访问器。
     */
    @Override
    public void afterPropertiesSet() {
        synchronized (REGISTRATION_MONITOR) {
            if (registered) {
                return;
            }
            // 多个 Spring 测试上下文可能同时存在，全局访问器只在第一个上下文启动时注册。
            if (registrationCount == 0) {
                contextRegistry.registerThreadLocalAccessor(accessor);
            }
            registrationCount++;
            registered = true;
        }
    }

    /**
     * 在应用上下文关闭时注销上下文访问器。
     */
    @Override
    public void destroy() {
        synchronized (REGISTRATION_MONITOR) {
            if (!registered) {
                return;
            }
            registrationCount--;
            registered = false;
            // 最后一个应用上下文关闭后再注销，避免并行上下文互相破坏传播能力。
            if (registrationCount == 0) {
                contextRegistry.removeThreadLocalAccessor(accessor.key());
            }
        }
    }
}
