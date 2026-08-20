package io.github.zhanghslq.muskit.idempotency.http;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 使用请求头和幂等状态存储保护 HTTP 写入请求的 Servlet Filter。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class HttpIdempotencyFilter extends OncePerRequestFilter {

    /** 默认 HTTP 幂等键请求头。 */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 返回幂等判定状态的响应头。 */
    public static final String IDEMPOTENCY_STATUS_HEADER = "Idempotency-Status";

    private final IdempotencyStore store;
    private final HttpIdempotencyOperationResolver operationResolver;
    private final HttpIdempotencyKeyResolver keyResolver;
    private final Duration processingTimeout;
    private final Duration retention;
    private final IntPredicate successStatus;

    /**
     * 使用固定操作名称和 Idempotency-Key 请求头创建 HTTP 幂等 Filter。
     *
     * @param store 幂等状态存储
     * @param operation 低基数 HTTP 操作名称
     * @param processingTimeout 请求处理超时时间
     * @param retention 成功状态保留时间
     */
    public HttpIdempotencyFilter(
            IdempotencyStore store,
            String operation,
            Duration processingTimeout,
            Duration retention) {
        this(
                store,
                request -> operation,
                request -> request.getHeader(IDEMPOTENCY_KEY_HEADER),
                processingTimeout,
                retention,
                status -> status >= 200 && status < 400);
    }

    /**
     * 使用自定义操作、业务键和成功状态策略创建 HTTP 幂等 Filter。
     *
     * @param store 幂等状态存储
     * @param operationResolver 低基数操作名称解析策略
     * @param keyResolver 业务幂等键解析策略
     * @param processingTimeout 请求处理超时时间
     * @param retention 成功状态保留时间
     * @param successStatus 判断响应是否成功的状态码策略
     */
    public HttpIdempotencyFilter(
            IdempotencyStore store,
            HttpIdempotencyOperationResolver operationResolver,
            HttpIdempotencyKeyResolver keyResolver,
            Duration processingTimeout,
            Duration retention,
            IntPredicate successStatus) {
        this.store = Objects.requireNonNull(store, "幂等状态存储不能为空");
        this.operationResolver = Objects.requireNonNull(operationResolver, "HTTP 幂等操作解析策略不能为空");
        this.keyResolver = Objects.requireNonNull(keyResolver, "HTTP 幂等键解析策略不能为空");
        this.processingTimeout = Objects.requireNonNull(processingTimeout, "HTTP 处理超时时间不能为空");
        this.retention = Objects.requireNonNull(retention, "HTTP 成功状态保留时间不能为空");
        this.successStatus = Objects.requireNonNull(successStatus, "HTTP 成功状态判断策略不能为空");
    }

    /**
     * 获取请求幂等所有权，并在同步或异步请求真正结束后提交或释放状态。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain Servlet Filter 链
     * @throws ServletException Filter 链执行异常
     * @throws IOException HTTP 输入输出异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String operation = operationResolver.resolve(request);
        String key = keyResolver.resolve(request);
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("HTTP 幂等操作名称不能为空");
        }
        if (key == null || key.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setHeader(IDEMPOTENCY_STATUS_HEADER, "missing-key");
            return;
        }

        IdempotencyAttempt attempt = store.tryStart(new IdempotencyRequest(
                operation, key, processingTimeout, retention));
        if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setHeader(IDEMPOTENCY_STATUS_HEADER, "in-progress");
            response.setHeader("Retry-After", "1");
            return;
        }
        if (attempt.decision() == IdempotencyDecision.COMPLETED) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.setHeader(IDEMPOTENCY_STATUS_HEADER, "completed");
            return;
        }
        IdempotencyClaim claim = attempt.claim().orElseThrow(
                () -> new IllegalStateException("幂等存储未返回 HTTP 请求所有权"));
        RequestCompletion completion = new RequestCompletion(claim, response);

        try {
            filterChain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                // 初始 Filter 线程返回不代表异步 Servlet 请求完成，由 AsyncListener 持有状态所有权。
                request.getAsyncContext().addListener(completion);
            } else {
                completion.finishFromStatus();
            }
        } catch (IOException | ServletException | RuntimeException | Error businessFailure) {
            completion.fail(businessFailure);
            throw businessFailure;
        }
    }

    /**
     * HTTP 请求幂等状态完成器，确保异步回调和异常路径只完成一次。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private final class RequestCompletion implements AsyncListener {

        private final IdempotencyClaim claim;
        private final HttpServletResponse response;
        private final AtomicBoolean finished = new AtomicBoolean();

        /**
         * 创建 HTTP 请求状态完成器。
         *
         * @param claim 幂等所有权声明
         * @param response HTTP 响应
         */
        private RequestCompletion(IdempotencyClaim claim, HttpServletResponse response) {
            this.claim = claim;
            this.response = response;
        }

        /**
         * 异步请求正常完成时根据最终状态码提交或释放状态。
         *
         * @param event 异步事件
         */
        @Override
        public void onComplete(AsyncEvent event) {
            finishFromStatus();
        }

        /**
         * 异步请求超时时释放状态，使客户端可以重试。
         *
         * @param event 异步事件
         */
        @Override
        public void onTimeout(AsyncEvent event) {
            releaseOnce(null);
        }

        /**
         * 异步请求失败时释放状态，并将状态异常附加到异步业务异常。
         *
         * @param event 异步事件
         */
        @Override
        public void onError(AsyncEvent event) {
            releaseOnce(event.getThrowable());
        }

        /**
         * 异步处理再次启动时将当前监听器注册到新的 AsyncContext。
         *
         * @param event 异步事件
         */
        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }

        /**
         * 根据最终 HTTP 状态码提交成功状态或释放失败状态。
         */
        private void finishFromStatus() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            if (successStatus.test(response.getStatus())) {
                store.complete(claim);
            } else {
                store.release(claim);
            }
        }

        /**
         * 同步业务失败时释放状态并保留状态异常。
         *
         * @param businessFailure 业务异常
         */
        private void fail(Throwable businessFailure) {
            releaseOnce(businessFailure);
        }

        /**
         * 原子释放状态，释放异常不覆盖已有业务异常。
         *
         * @param businessFailure 业务异常，可为空
         */
        private void releaseOnce(Throwable businessFailure) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try {
                store.release(claim);
            } catch (RuntimeException stateFailure) {
                if (businessFailure == null) {
                    throw stateFailure;
                }
                businessFailure.addSuppressed(stateFailure);
            }
        }
    }
}
