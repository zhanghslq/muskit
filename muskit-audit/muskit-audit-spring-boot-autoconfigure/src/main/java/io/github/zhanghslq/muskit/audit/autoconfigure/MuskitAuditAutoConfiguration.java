package io.github.zhanghslq.muskit.audit.autoconfigure;

import java.util.Optional;

import io.github.zhanghslq.muskit.audit.AuditFailureListener;
import io.github.zhanghslq.muskit.audit.AuditPrincipalProvider;
import io.github.zhanghslq.muskit.audit.AuditRecorder;
import io.github.zhanghslq.muskit.audit.AuditWriter;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 审计记录器和注解切面自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration(after = MuskitAuditJdbcAutoConfiguration.class)
@ConditionalOnBean(AuditWriter.class)
@ConditionalOnProperty(prefix = "muskit.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitAuditProperties.class)
public class MuskitAuditAutoConfiguration {

    /**
     * 创建审计主自动配置。
     */
    public MuskitAuditAutoConfiguration() {
    }

    /**
     * 创建默认空操作者 Provider，业务可显式覆盖。
     *
     * @return 操作者 Provider
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditPrincipalProvider muskitAuditPrincipalProvider() {
        return Optional::empty;
    }

    /**
     * 创建默认日志失败监听器。
     *
     * @return 审计失败监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditFailureListener muskitAuditFailureListener() {
        return new LoggingAuditFailureListener();
    }

    /**
     * 创建审计记录器。
     *
     * @param writer 审计 Writer
     * @param principalProvider 操作者 Provider
     * @param failureListener 失败监听器
     * @param properties 审计配置
     * @param registryProvider 统一观测注册器 Provider
     * @return 审计记录器
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditRecorder muskitAuditRecorder(
            AuditWriter writer,
            AuditPrincipalProvider principalProvider,
            AuditFailureListener failureListener,
            MuskitAuditProperties properties,
            ObjectProvider<MuskitObservationRegistry> registryProvider) {
        return new AuditRecorder(
                writer,
                principalProvider,
                failureListener,
                properties.getFailureMode(),
                registryProvider.getIfAvailable(MuskitObservationRegistry::noop));
    }

    /**
     * 创建注解审计切面。
     *
     * @param recorder 审计记录器
     * @return 审计切面
     */
    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnMissingBean
    public AuditOperationAspect muskitAuditOperationAspect(AuditRecorder recorder) {
        return new AuditOperationAspect(recorder);
    }
}
