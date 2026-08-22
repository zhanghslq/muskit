package io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive;

import io.github.zhanghslq.muskit.lifecycle.autoconfigure.MuskitLifecycleAutoConfiguration;
import io.github.zhanghslq.muskit.lifecycle.autoconfigure.MuskitLifecycleProperties;
import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * 仅在响应式 Web 应用中启用的 HTTP 排空自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitLifecycleAutoConfiguration.class)
@ConditionalOnClass({WebFilter.class, Mono.class, DrainWebFilter.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "muskit.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MuskitLifecycleReactiveAutoConfiguration {

    /**
     * 创建响应式生命周期自动配置。
     */
    public MuskitLifecycleReactiveAutoConfiguration() {
    }

    /**
     * 创建 WebFlux HTTP 排空过滤器。
     *
     * @param drainController HTTP 请求排空控制器
     * @param properties 生命周期配置
     * @return WebFlux 排空过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.lifecycle",
            name = "http-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DrainWebFilter muskitDrainWebFilter(
            @Qualifier("muskitRequestDrainController") DrainController drainController,
            MuskitLifecycleProperties properties) {
        return new DrainWebFilter(
                drainController,
                properties.getRejectedStatus(),
                properties.getRetryAfterSeconds(),
                properties.getExcludedPathPrefixes());
    }
}
