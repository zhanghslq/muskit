package io.github.zhanghslq.muskit.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * HTTP 调用链 Deadline 和业务上下文传播策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ClientPropagationPolicy {

    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private final Duration outboundTimeout;
    private final Duration maxInboundTimeout;
    private final int maxHeaderValueLength;
    private final Map<String, String> contextHeaders;

    /**
     * 创建传播策略。
     *
     * @param outboundTimeout 出站调用默认且最大预算
     * @param maxInboundTimeout 入站允许接受的最大预算
     * @param maxHeaderValueLength 单个上下文请求头最大长度
     * @param contextHeaders 业务上下文键到请求头名称的白名单映射
     */
    public ClientPropagationPolicy(
            Duration outboundTimeout,
            Duration maxInboundTimeout,
            int maxHeaderValueLength,
            Map<String, String> contextHeaders) {
        this.outboundTimeout = requirePositive(outboundTimeout, "出站调用预算必须大于零");
        this.maxInboundTimeout = requirePositive(maxInboundTimeout, "入站调用最大预算必须大于零");
        if (maxHeaderValueLength < 1 || maxHeaderValueLength > 8192) {
            throw new IllegalArgumentException("上下文请求头最大长度必须在 1 到 8192 之间");
        }
        this.maxHeaderValueLength = maxHeaderValueLength;
        Objects.requireNonNull(contextHeaders, "业务上下文请求头映射不能为空");
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        contextHeaders.forEach((contextKey, headerName) -> {
            if (contextKey == null || contextKey.isBlank()) {
                throw new IllegalArgumentException("业务上下文键不能为空");
            }
            if (headerName == null || !HEADER_NAME.matcher(headerName).matches()) {
                throw new IllegalArgumentException("业务上下文请求头名称不合法");
            }
            copied.put(contextKey, headerName);
        });
        if (copied.values().stream().distinct().count() != copied.size()) {
            throw new IllegalArgumentException("不同业务上下文键不能映射到同一个请求头");
        }
        this.contextHeaders = Map.copyOf(copied);
    }

    /**
     * 返回出站调用默认且最大预算。
     *
     * @return 出站调用预算
     */
    public Duration outboundTimeout() {
        return outboundTimeout;
    }

    /**
     * 返回入站允许接受的最大预算。
     *
     * @return 入站最大预算
     */
    public Duration maxInboundTimeout() {
        return maxInboundTimeout;
    }

    /**
     * 返回单个上下文请求头最大长度。
     *
     * @return 最大长度
     */
    public int maxHeaderValueLength() {
        return maxHeaderValueLength;
    }

    /**
     * 返回不可变的业务上下文请求头白名单。
     *
     * @return 上下文键到请求头名称的映射
     */
    public Map<String, String> contextHeaders() {
        return contextHeaders;
    }

    /**
     * 校验时长为正数。
     *
     * @param value 待校验时长
     * @param message 校验失败消息
     * @return 原时长
     */
    private static Duration requirePositive(Duration value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
