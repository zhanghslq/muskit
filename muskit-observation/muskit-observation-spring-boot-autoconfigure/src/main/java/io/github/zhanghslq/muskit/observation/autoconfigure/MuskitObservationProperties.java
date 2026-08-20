package io.github.zhanghslq.muskit.observation.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 统一可观测性配置属性。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.observability")
public class MuskitObservationProperties {

    private boolean enabled = true;
    private boolean endpointEnabled = true;

    /**
     * 创建统一可观测性配置属性。
     */
    public MuskitObservationProperties() {
    }

    /**
     * 返回统一指标是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置统一指标是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 Muskit Actuator Endpoint 是否启用。
     *
     * @return 是否启用 Endpoint
     */
    public boolean isEndpointEnabled() {
        return endpointEnabled;
    }

    /**
     * 设置 Muskit Actuator Endpoint 是否启用。
     *
     * @param endpointEnabled 是否启用 Endpoint
     */
    public void setEndpointEnabled(boolean endpointEnabled) {
        this.endpointEnabled = endpointEnabled;
    }
}
