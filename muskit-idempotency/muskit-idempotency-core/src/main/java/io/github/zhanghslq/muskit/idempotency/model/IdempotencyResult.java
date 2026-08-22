package io.github.zhanghslq.muskit.idempotency.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 可由幂等状态存储持久化并重放的 HTTP 结果。
 *
 * @param statusCode HTTP 状态码
 * @param contentType 响应内容类型，可为空字符串
 * @param headers 允许重放的响应头
 * @param body 响应体
 * @author zhs
 * @since 2026-08-20
 */
public record IdempotencyResult(
        int statusCode,
        String contentType,
        Map<String, String> headers,
        byte[] body) {

    /**
     * 校验并防御性复制可重放结果。
     */
    public IdempotencyResult {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("HTTP 状态码必须在 100 到 599 之间");
        }
        contentType = contentType == null ? "" : contentType;
        Objects.requireNonNull(headers, "响应头不能为空");
        Map<String, String> copiedHeaders = new LinkedHashMap<>();
        headers.forEach((name, value) -> copiedHeaders.put(
                Objects.requireNonNull(name, "响应头名称不能为空"),
                Objects.requireNonNull(value, "响应头值不能为空")));
        headers = Map.copyOf(copiedHeaders);
        body = Objects.requireNonNull(body, "响应体不能为空").clone();
    }

    /**
     * 返回响应体的防御性副本。
     *
     * @return 响应体副本
     */
    @Override
    public byte[] body() {
        return body.clone();
    }
}
