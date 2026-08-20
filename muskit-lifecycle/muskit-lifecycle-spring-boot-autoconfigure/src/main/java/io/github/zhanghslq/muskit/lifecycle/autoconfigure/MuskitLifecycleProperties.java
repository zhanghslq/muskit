package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Muskit 优雅摘流和排空配置。
 *
 * @author zhs
 * @since 2026-08-20
 */
@ConfigurationProperties("muskit.lifecycle")
public class MuskitLifecycleProperties {

    private boolean enabled = true;
    private boolean httpEnabled = true;
    private Duration shutdownTimeout = Duration.ofSeconds(30);
    private int rejectedStatus = 503;
    private int retryAfterSeconds = 5;
    private List<String> excludedPathPrefixes = new ArrayList<>(List.of("/actuator/health"));

    /**
     * 创建默认生命周期配置。
     */
    public MuskitLifecycleProperties() {
    }

    /**
     * 返回是否启用生命周期治理。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用生命周期治理。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回是否启用 HTTP 在途请求统计和摘流拒绝。
     *
     * @return 是否启用 HTTP 治理
     */
    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    /**
     * 设置是否启用 HTTP 治理。
     *
     * @param httpEnabled 是否启用 HTTP 治理
     */
    public void setHttpEnabled(boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }

    /**
     * 返回全局排空最大等待时间。
     *
     * @return 排空超时
     */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * 设置全局排空最大等待时间。
     *
     * @param shutdownTimeout 排空超时
     */
    public void setShutdownTimeout(Duration shutdownTimeout) {
        if (shutdownTimeout == null || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("生命周期排空超时不能为负数");
        }
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * 返回摘流后新请求的 HTTP 状态码。
     *
     * @return HTTP 状态码
     */
    public int getRejectedStatus() {
        return rejectedStatus;
    }

    /**
     * 设置摘流拒绝状态码。
     *
     * @param rejectedStatus HTTP 状态码
     */
    public void setRejectedStatus(int rejectedStatus) {
        if (rejectedStatus < 400 || rejectedStatus > 599) {
            throw new IllegalArgumentException("摘流拒绝状态码必须是 4xx 或 5xx");
        }
        this.rejectedStatus = rejectedStatus;
    }

    /**
     * 返回拒绝响应 Retry-After 秒数。
     *
     * @return 建议重试秒数
     */
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * 设置拒绝响应 Retry-After 秒数。
     *
     * @param retryAfterSeconds 建议重试秒数
     */
    public void setRetryAfterSeconds(int retryAfterSeconds) {
        if (retryAfterSeconds < 0) {
            throw new IllegalArgumentException("Retry-After 秒数不能为负数");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 返回排空期间仍允许访问的路径前缀。
     *
     * @return 排除路径前缀
     */
    public List<String> getExcludedPathPrefixes() {
        return List.copyOf(excludedPathPrefixes);
    }

    /**
     * 设置排空期间仍允许访问的路径前缀。
     *
     * @param excludedPathPrefixes 排除路径前缀
     */
    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        if (excludedPathPrefixes == null
                || excludedPathPrefixes.stream().anyMatch(path -> path == null || path.isBlank())) {
            throw new IllegalArgumentException("生命周期排除路径前缀不能为空");
        }
        this.excludedPathPrefixes = new ArrayList<>(excludedPathPrefixes);
    }
}
