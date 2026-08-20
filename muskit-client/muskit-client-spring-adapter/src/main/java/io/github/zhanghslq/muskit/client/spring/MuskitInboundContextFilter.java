package io.github.zhanghslq.muskit.client.spring;

import java.io.IOException;
import java.util.Objects;

import io.github.zhanghslq.muskit.client.ClientPropagation;
import io.github.zhanghslq.muskit.client.InvalidPropagationHeaderException;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineExceededException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 在 Servlet 请求处理期间恢复可信调用链上下文的过滤器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitInboundContextFilter implements Filter {

    private final ClientPropagation propagation;

    /**
     * 创建入站调用链过滤器。
     *
     * @param propagation 调用链传播器
     */
    public MuskitInboundContextFilter(ClientPropagation propagation) {
        this.propagation = Objects.requireNonNull(propagation, "调用链传播器不能为空");
    }

    /**
     * 恢复请求上下文；非法请求头返回 400，已耗尽预算返回 504。
     *
     * @param request Servlet 请求
     * @param response Servlet 响应
     * @param chain 过滤器链
     * @throws IOException IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        try (ClientPropagation.InboundScope ignored = propagation.openInbound(httpRequest::getHeader)) {
            chain.doFilter(request, response);
        } catch (InvalidPropagationHeaderException exception) {
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Muskit propagation headers");
        } catch (DeadlineExceededException exception) {
            httpResponse.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Request deadline exceeded");
        }
    }
}
