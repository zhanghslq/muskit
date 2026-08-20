package io.github.zhanghslq.muskit.client.autoconfigure;

import io.github.zhanghslq.muskit.client.ClientPropagation;
import io.github.zhanghslq.muskit.client.spring.MuskitClientHttpRequestInterceptor;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Spring RestClient 出站调用治理自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitClientAutoConfiguration.class)
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class, MuskitClientHttpRequestInterceptor.class})
@ConditionalOnProperty(prefix = "muskit.client", name = {"enabled", "outbound-enabled"}, havingValue = "true", matchIfMissing = true)
public class MuskitRestClientAutoConfiguration {

    /**
     * 创建 RestClient 自动配置。
     */
    public MuskitRestClientAutoConfiguration() {
    }

    /**
     * 创建 Muskit HTTP 请求拦截器。
     *
     * @param propagation 调用链传播器
     * @param properties 客户端配置
     * @param registryProvider 统一观测注册器 Provider
     * @return HTTP 请求拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public MuskitClientHttpRequestInterceptor muskitClientHttpRequestInterceptor(
            ClientPropagation propagation,
            MuskitClientProperties properties,
            ObjectProvider<MuskitObservationRegistry> registryProvider) {
        return new MuskitClientHttpRequestInterceptor(
                propagation,
                registryProvider.getIfAvailable(MuskitObservationRegistry::noop),
                properties.getOperation());
    }

    /**
     * 创建向所有自动配置 RestClient.Builder 注册拦截器的定制器。
     *
     * @param interceptor Muskit HTTP 请求拦截器
     * @return RestClient 定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "muskitRestClientCustomizer")
    public RestClientCustomizer muskitRestClientCustomizer(MuskitClientHttpRequestInterceptor interceptor) {
        return builder -> builder.requestInterceptor(interceptor);
    }
}
