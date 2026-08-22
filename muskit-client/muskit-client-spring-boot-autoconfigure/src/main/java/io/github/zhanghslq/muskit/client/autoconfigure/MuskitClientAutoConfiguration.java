package io.github.zhanghslq.muskit.client.autoconfigure;

import io.github.zhanghslq.muskit.client.model.ClientPropagationPolicy;
import io.github.zhanghslq.muskit.client.service.ClientPropagation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 与 HTTP 客户端实现无关的调用链传播自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "muskit.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitClientProperties.class)
public class MuskitClientAutoConfiguration {

    /**
     * 创建客户端传播主自动配置。
     */
    public MuskitClientAutoConfiguration() {
    }

    /**
     * 创建调用链传播策略。
     *
     * @param properties 客户端配置
     * @return 调用链传播策略
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientPropagationPolicy muskitClientPropagationPolicy(MuskitClientProperties properties) {
        return new ClientPropagationPolicy(
                properties.getOutboundTimeout(),
                properties.getMaxInboundTimeout(),
                properties.getMaxHeaderValueLength(),
                properties.getContextHeaders());
    }

    /**
     * 创建调用链传播器。
     *
     * @param policy 调用链传播策略
     * @return 调用链传播器
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientPropagation muskitClientPropagation(ClientPropagationPolicy policy) {
        return new ClientPropagation(policy);
    }
}
