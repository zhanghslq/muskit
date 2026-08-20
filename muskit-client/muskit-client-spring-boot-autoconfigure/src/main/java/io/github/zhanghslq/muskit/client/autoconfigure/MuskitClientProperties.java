package io.github.zhanghslq.muskit.client.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit HTTP 客户端调用链传播配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.client")
public class MuskitClientProperties {

    private boolean enabled = true;
    private boolean outboundEnabled = true;
    private boolean inboundEnabled = true;
    private Duration outboundTimeout = Duration.ofSeconds(3);
    private Duration maxInboundTimeout = Duration.ofSeconds(30);
    private int maxHeaderValueLength = 512;
    private String operation = "http";
    private int filterOrder = -200;
    private Map<String, String> contextHeaders = new LinkedHashMap<>();

    /**
     * 返回是否启用客户端治理。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用客户端治理。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回是否自动定制出站 RestClient。
     *
     * @return 是否启用出站传播
     */
    public boolean isOutboundEnabled() {
        return outboundEnabled;
    }

    /**
     * 设置是否自动定制出站 RestClient。
     *
     * @param outboundEnabled 是否启用出站传播
     */
    public void setOutboundEnabled(boolean outboundEnabled) {
        this.outboundEnabled = outboundEnabled;
    }

    /**
     * 返回是否启用 Servlet 入站恢复。
     *
     * @return 是否启用入站恢复
     */
    public boolean isInboundEnabled() {
        return inboundEnabled;
    }

    /**
     * 设置是否启用 Servlet 入站恢复。
     *
     * @param inboundEnabled 是否启用入站恢复
     */
    public void setInboundEnabled(boolean inboundEnabled) {
        this.inboundEnabled = inboundEnabled;
    }

    /**
     * 返回出站调用默认且最大预算。
     *
     * @return 出站预算
     */
    public Duration getOutboundTimeout() {
        return outboundTimeout;
    }

    /**
     * 设置出站调用默认且最大预算。
     *
     * @param outboundTimeout 出站预算
     */
    public void setOutboundTimeout(Duration outboundTimeout) {
        this.outboundTimeout = outboundTimeout;
    }

    /**
     * 返回入站允许接受的最大预算。
     *
     * @return 入站最大预算
     */
    public Duration getMaxInboundTimeout() {
        return maxInboundTimeout;
    }

    /**
     * 设置入站允许接受的最大预算。
     *
     * @param maxInboundTimeout 入站最大预算
     */
    public void setMaxInboundTimeout(Duration maxInboundTimeout) {
        this.maxInboundTimeout = maxInboundTimeout;
    }

    /**
     * 返回上下文请求头最大长度。
     *
     * @return 最大长度
     */
    public int getMaxHeaderValueLength() {
        return maxHeaderValueLength;
    }

    /**
     * 设置上下文请求头最大长度。
     *
     * @param maxHeaderValueLength 最大长度
     */
    public void setMaxHeaderValueLength(int maxHeaderValueLength) {
        this.maxHeaderValueLength = maxHeaderValueLength;
    }

    /**
     * 返回用于指标的稳定操作名称。
     *
     * @return 操作名称
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 设置用于指标的稳定操作名称。
     *
     * @param operation 操作名称
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * 返回入站过滤器顺序。
     *
     * @return 过滤器顺序
     */
    public int getFilterOrder() {
        return filterOrder;
    }

    /**
     * 设置入站过滤器顺序。
     *
     * @param filterOrder 过滤器顺序
     */
    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    /**
     * 返回上下文键到请求头名称的白名单映射。
     *
     * @return 白名单映射
     */
    public Map<String, String> getContextHeaders() {
        return contextHeaders;
    }

    /**
     * 设置上下文键到请求头名称的白名单映射。
     *
     * @param contextHeaders 白名单映射
     */
    public void setContextHeaders(Map<String, String> contextHeaders) {
        this.contextHeaders = contextHeaders;
    }
}
