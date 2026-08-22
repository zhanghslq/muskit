package io.github.zhanghslq.muskit.client.autoconfigure.servlet;

import io.github.zhanghslq.muskit.client.service.ClientPropagation;
import io.github.zhanghslq.muskit.client.autoconfigure.MuskitClientAutoConfiguration;
import io.github.zhanghslq.muskit.client.autoconfigure.MuskitClientProperties;
import io.github.zhanghslq.muskit.client.spring.MuskitInboundContextFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Servlet 入站调用链上下文恢复自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitClientAutoConfiguration.class)
@ConditionalOnClass({Servlet.class, Filter.class, MuskitInboundContextFilter.class})
@ConditionalOnProperty(prefix = "muskit.client", name = {"enabled", "inbound-enabled"}, havingValue = "true", matchIfMissing = true)
public class MuskitClientServletAutoConfiguration {

    /**
     * 创建 Servlet 入站自动配置。
     */
    public MuskitClientServletAutoConfiguration() {
    }

    /**
     * 注册入站调用链过滤器。
     *
     * @param propagation 调用链传播器
     * @param properties 客户端配置
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "muskitInboundContextFilterRegistration")
    public FilterRegistrationBean<MuskitInboundContextFilter> muskitInboundContextFilterRegistration(
            ClientPropagation propagation,
            MuskitClientProperties properties) {
        FilterRegistrationBean<MuskitInboundContextFilter> registration =
                new FilterRegistrationBean<>(new MuskitInboundContextFilter(propagation));
        registration.setName("muskitInboundContextFilter");
        registration.setOrder(properties.getFilterOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }
}
