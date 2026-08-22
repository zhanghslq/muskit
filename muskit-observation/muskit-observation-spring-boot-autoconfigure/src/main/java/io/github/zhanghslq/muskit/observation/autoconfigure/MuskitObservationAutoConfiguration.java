package io.github.zhanghslq.muskit.observation.autoconfigure;

import io.github.zhanghslq.muskit.observation.autoconfigure.endpoint.MuskitEndpoint;
import io.github.zhanghslq.muskit.observation.micrometer.MicrometerMuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Muskit 统一 Micrometer 指标和 Actuator Endpoint 自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "muskit.observability",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MuskitObservationProperties.class)
public class MuskitObservationAutoConfiguration {

    /**
     * 创建统一可观测性自动配置。
     */
    public MuskitObservationAutoConfiguration() {
    }

    /**
     * 创建 Micrometer Muskit 指标注册器。
     *
     * @param meterRegistry Micrometer 注册器
     * @return Muskit 指标注册器
     */
    @Bean
    @ConditionalOnClass({MeterRegistry.class, MicrometerMuskitObservationRegistry.class})
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public MuskitObservationRegistry muskitObservationRegistry(MeterRegistry meterRegistry) {
        return new MicrometerMuskitObservationRegistry(meterRegistry);
    }

    /**
     * 创建低基数 Muskit 运行快照 Endpoint。
     *
     * @param beanFactory Spring Bean 工厂
     * @param environment Spring 配置环境
     * @return Muskit Endpoint
     */
    @Bean
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "muskit.observability",
            name = "endpoint-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public MuskitEndpoint muskitEndpoint(ListableBeanFactory beanFactory, Environment environment) {
        return new MuskitEndpoint(beanFactory, environment);
    }
}
