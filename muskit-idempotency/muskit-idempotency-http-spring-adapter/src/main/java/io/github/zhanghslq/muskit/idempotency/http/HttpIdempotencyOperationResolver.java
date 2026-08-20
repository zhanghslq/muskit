package io.github.zhanghslq.muskit.idempotency.http;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从 HTTP 请求中解析低基数操作名称的策略接口。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface HttpIdempotencyOperationResolver {

    /**
     * 解析 HTTP 幂等操作名称。
     *
     * @param request HTTP 请求
     * @return 低基数操作名称
     */
    String resolve(HttpServletRequest request);
}
