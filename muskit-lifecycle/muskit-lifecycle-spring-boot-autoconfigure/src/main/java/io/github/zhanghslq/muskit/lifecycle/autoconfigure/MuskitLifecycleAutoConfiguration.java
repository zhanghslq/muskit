package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import java.util.List;

import io.github.zhanghslq.muskit.lifecycle.DrainController;
import io.github.zhanghslq.muskit.lifecycle.DrainCoordinator;
import io.github.zhanghslq.muskit.lifecycle.Drainable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Muskit 优雅摘流和组件排空自动配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "muskit.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MuskitLifecycleProperties.class)
public class MuskitLifecycleAutoConfiguration {

    /**
     * 创建生命周期自动配置。
     */
    public MuskitLifecycleAutoConfiguration() {
    }

    /**
     * 创建 HTTP 请求专用排空控制器。
     *
     * @return HTTP 请求排空控制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "muskitRequestDrainController")
    public DrainController muskitRequestDrainController() {
        return new DrainController("http-requests");
    }

    /**
     * 汇总应用中的全部可排空组件。
     *
     * @param drainables 可排空组件 Provider
     * @return 全局排空协调器
     */
    @Bean
    @ConditionalOnMissingBean
    public DrainCoordinator muskitDrainCoordinator(ObjectProvider<Drainable> drainables) {
        List<Drainable> components = drainables.orderedStream().toList();
        return new DrainCoordinator(components);
    }

    /**
     * 创建与 Spring Boot Readiness 联动的排空生命周期。
     *
     * @param coordinator 全局排空协调器
     * @param drainController HTTP 请求排空控制器
     * @param applicationContext Spring 应用上下文
     * @param properties 生命周期配置
     * @return 排空生命周期
     */
    @Bean
    @ConditionalOnMissingBean
    public MuskitDrainLifecycle muskitDrainLifecycle(
            DrainCoordinator coordinator,
            @Qualifier("muskitRequestDrainController") DrainController drainController,
            ApplicationContext applicationContext,
            MuskitLifecycleProperties properties) {
        return new MuskitDrainLifecycle(
                coordinator,
                drainController,
                applicationContext,
                properties.getShutdownTimeout());
    }
}
