package io.github.zhanghslq.muskit.state.autoconfigure;

import io.github.zhanghslq.muskit.state.StateMachineFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 状态机工厂自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "muskit.state", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitStateProperties.class)
public class MuskitStateAutoConfiguration {

    /**
     * 创建状态机自动配置。
     */
    public MuskitStateAutoConfiguration() {
    }

    /**
     * 创建使用统一乐观锁重试配置的状态机工厂。
     *
     * @param properties 状态机配置
     * @return 状态机工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StateMachineFactory muskitStateMachineFactory(MuskitStateProperties properties) {
        return new StateMachineFactory(properties.getMaxConflictRetries());
    }
}
