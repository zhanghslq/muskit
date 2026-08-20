package io.github.zhanghslq.muskit.idempotency.http;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求中解析业务幂等键的策略接口。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface HttpIdempotencyKeyResolver {

    /**
     * 解析 HTTP 请求幂等键。
     *
     * @param request HTTP 请求
     * @return 业务幂等键
     */
    String resolve(HttpServletRequest request);
}
