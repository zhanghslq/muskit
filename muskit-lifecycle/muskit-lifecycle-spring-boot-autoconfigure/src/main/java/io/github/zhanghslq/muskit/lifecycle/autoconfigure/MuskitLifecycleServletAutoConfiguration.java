package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import io.github.zhanghslq.muskit.lifecycle.DrainController;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 仅在 Servlet 技术栈存在时启用的 HTTP 排空自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitLifecycleAutoConfiguration.class)
@ConditionalOnClass({Filter.class, DrainHttpFilter.class})
@ConditionalOnProperty(prefix = "muskit.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MuskitLifecycleServletAutoConfiguration {

    /**
     * 创建 Servlet 生命周期自动配置。
     */
    public MuskitLifecycleServletAutoConfiguration() {
    }

    /**
     * 创建 Servlet HTTP 排空过滤器。
     *
     * @param drainController HTTP 请求排空控制器
     * @param properties 生命周期配置
     * @return HTTP 排空过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.lifecycle",
            name = "http-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public DrainHttpFilter muskitDrainHttpFilter(
            @Qualifier("muskitRequestDrainController") DrainController drainController,
            MuskitLifecycleProperties properties) {
        return new DrainHttpFilter(
                drainController,
                properties.getRejectedStatus(),
                properties.getRetryAfterSeconds(),
                properties.getExcludedPathPrefixes());
    }
}
