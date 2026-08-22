package io.github.zhanghslq.muskit.inbox.autoconfigure;

import io.github.zhanghslq.muskit.inbox.autoconfigure.jdbc.MuskitInboxJdbcAutoConfiguration;
import io.github.zhanghslq.muskit.inbox.service.InboxProcessor;
import io.github.zhanghslq.muskit.inbox.service.InboxTemplate;
import io.github.zhanghslq.muskit.inbox.spi.InboxPolicyResolver;
import io.github.zhanghslq.muskit.inbox.spi.InboxStore;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 与具体存储技术无关的 Inbox 处理器自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitInboxJdbcAutoConfiguration.class)
@ConditionalOnBean(InboxStore.class)
@ConditionalOnProperty(prefix = "muskit.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitInboxProperties.class)
public class MuskitInboxAutoConfiguration {

    /**
     * 创建 Inbox 主自动配置。
     */
    public MuskitInboxAutoConfiguration() {
    }

    /**
     * 创建配置属性策略解析器。
     *
     * @param properties Inbox 配置
     * @return Inbox 策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public InboxPolicyResolver muskitInboxPolicyResolver(MuskitInboxProperties properties) {
        return new PropertiesInboxPolicyResolver(properties);
    }

    /**
     * 创建 Inbox 状态机处理器。
     *
     * @param store Inbox 存储
     * @param observationRegistryProvider 统一观测注册器 Provider
     * @return Inbox 处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public InboxProcessor muskitInboxProcessor(
            InboxStore store,
            ObjectProvider<MuskitObservationRegistry> observationRegistryProvider) {
        return new InboxProcessor(
                store,
                observationRegistryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }

    /**
     * 创建面向业务的 Inbox 模板。
     *
     * @param processor Inbox 处理器
     * @param policyResolver 策略解析器
     * @return Inbox 模板
     */
    @Bean
    @ConditionalOnMissingBean
    public InboxTemplate muskitInboxTemplate(
            InboxProcessor processor,
            InboxPolicyResolver policyResolver) {
        return new InboxTemplate(processor, policyResolver);
    }
}
