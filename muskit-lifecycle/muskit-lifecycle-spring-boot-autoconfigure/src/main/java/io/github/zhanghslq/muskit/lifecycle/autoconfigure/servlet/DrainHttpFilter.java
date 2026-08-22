package io.github.zhanghslq.muskit.lifecycle.autoconfigure.servlet;

import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import io.github.zhanghslq.muskit.lifecycle.service.DrainPermit;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在排空期间拒绝新 HTTP 请求，并跟踪同步及 Servlet 异步请求的真实完成时间。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainHttpFilter extends OncePerRequestFilter {

    private static final String REJECTED_BODY = "{\"title\":\"Service is draining\",\"status\":503}";

    private final DrainController drainController;
    private final int rejectedStatus;
    private final int retryAfterSeconds;
    private final List<String> excludedPathPrefixes;

    /**
     * 创建 HTTP 排空过滤器。
     *
     * @param drainController HTTP 请求排空控制器
     * @param rejectedStatus 摘流拒绝状态码
     * @param retryAfterSeconds 建议重试秒数
     * @param excludedPathPrefixes 排空期间放行的路径前缀
     */
    public DrainHttpFilter(
            DrainController drainController,
            int rejectedStatus,
            int retryAfterSeconds,
            List<String> excludedPathPrefixes) {
        this.drainController = Objects.requireNonNull(drainController, "HTTP 排空控制器不能为空");
        this.rejectedStatus = rejectedStatus;
        this.retryAfterSeconds = retryAfterSeconds;
        this.excludedPathPrefixes = List.copyOf(excludedPathPrefixes);
    }

    /**
     * 对健康检查等显式排除路径跳过排空控制。
     *
     * @param request HTTP 请求
     * @return 是否跳过过滤
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPathPrefixes.stream().anyMatch(path::startsWith);
    }

    /**
     * 获取在途许可，Servlet 异步请求将许可延迟到真正完成、超时或异常时释放。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DrainPermit permit = drainController.tryEnter().orElse(null);
        if (permit == null) {
            response.setStatus(rejectedStatus);
            response.setHeader("Retry-After", Integer.toString(retryAfterSeconds));
            response.setContentType("application/problem+json");
            response.getWriter().write(REJECTED_BODY.replace("503", Integer.toString(rejectedStatus)));
            return;
        }

        boolean asyncListenerRegistered = false;
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.isAsyncStarted()) {
                try {
                    request.getAsyncContext().addListener(new PermitAsyncListener(permit));
                    asyncListenerRegistered = true;
                } catch (IllegalStateException asyncAlreadyCompleted) {
                    // 异步上下文可能在注册监听器前已经完成，此时由当前线程安全释放许可。
                }
            }
            if (!asyncListenerRegistered) {
                permit.close();
            }
        }
    }

    /**
     * 在 Servlet 异步请求真实结束时释放排空许可。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class PermitAsyncListener implements AsyncListener {

        private final DrainPermit permit;

        /**
         * 创建异步完成监听器。
         *
         * @param permit 待释放许可
         */
        private PermitAsyncListener(DrainPermit permit) {
            this.permit = permit;
        }

        /**
         * 异步请求正常完成时释放许可。
         *
         * @param event 异步事件
         */
        @Override
        public void onComplete(AsyncEvent event) {
            permit.close();
        }

        /**
         * 异步请求超时时释放许可。
         *
         * @param event 异步事件
         */
        @Override
        public void onTimeout(AsyncEvent event) {
            permit.close();
        }

        /**
         * 异步请求异常时释放许可。
         *
         * @param event 异步事件
         */
        @Override
        public void onError(AsyncEvent event) {
            permit.close();
        }

        /**
         * 异步周期再次启动时把同一个幂等监听器注册到新上下文。
         *
         * @param event 新异步周期事件
         */
        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
