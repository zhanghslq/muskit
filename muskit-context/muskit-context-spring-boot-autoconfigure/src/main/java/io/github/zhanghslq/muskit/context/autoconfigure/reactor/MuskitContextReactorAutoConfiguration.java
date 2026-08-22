package io.github.zhanghslq.muskit.context.autoconfigure.reactor;

import io.github.zhanghslq.muskit.context.autoconfigure.MuskitContextAutoConfiguration;
import io.github.zhanghslq.muskit.context.autoconfigure.propagation.MuskitContextAccessorRegistrar;
import io.github.zhanghslq.muskit.context.reactor.MuskitReactorContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Hooks;

/**
 * Reactor 自动上下文传播的可选自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitContextAutoConfiguration.class)
@ConditionalOnClass({Hooks.class, MuskitReactorContext.class})
@ConditionalOnBean(MuskitContextAccessorRegistrar.class)
@ConditionalOnProperty(prefix = "muskit.context", name = "reactor-enabled", havingValue = "true", matchIfMissing = true)
public class MuskitContextReactorAutoConfiguration {

    /**
     * 创建 Reactor 上下文传播自动配置。
     */
    public MuskitContextReactorAutoConfiguration() {
    }

    /**
     * 创建 Reactor 自动传播钩子注册器。
     *
     * @return Reactor 上下文传播注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public MuskitReactorContextPropagationRegistrar muskitReactorContextPropagationRegistrar() {
        return new MuskitReactorContextPropagationRegistrar();
    }
}
