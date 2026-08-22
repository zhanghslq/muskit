package io.github.zhanghslq.muskit.context.autoconfigure.reactor;

import org.springframework.beans.factory.InitializingBean;
import reactor.core.publisher.Hooks;

/**
 * 在应用启动时启用 Reactor 与 Micrometer ThreadLocalAccessor 的自动桥接。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitReactorContextPropagationRegistrar implements InitializingBean {

    /**
     * 创建 Reactor 上下文传播注册器。
     */
    public MuskitReactorContextPropagationRegistrar() {
    }

    /**
     * 启用 Reactor 全局自动上下文传播钩子。
     *
     * <p>该钩子是 Reactor 提供的进程级幂等开关。应用上下文关闭时不主动关闭，
     * 避免破坏同一 JVM 中其他 Spring 上下文或业务代码启用的传播能力。</p>
     */
    @Override
    public void afterPropertiesSet() {
        Hooks.enableAutomaticContextPropagation();
    }
}
