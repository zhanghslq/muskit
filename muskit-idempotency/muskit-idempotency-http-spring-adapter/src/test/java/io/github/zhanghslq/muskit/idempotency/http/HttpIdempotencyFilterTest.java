package io.github.zhanghslq.muskit.idempotency.http;

import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyResult;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP 幂等 Filter 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class HttpIdempotencyFilterTest {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration RETENTION = Duration.ofHours(1);

    /**
     * 验证同步成功响应提交幂等成功状态。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldCompleteSuccessfulSynchronousRequest() throws Exception {
        IdempotencyStore store = acquiredStore();
        MockHttpServletRequest request = request("request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(store).doFilter(request, response, (currentRequest, currentResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) currentResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("Location", "/orders/42");
            httpResponse.getWriter().write("{\"orderId\":42}");
        });

        ArgumentCaptor<IdempotencyResult> result = ArgumentCaptor.forClass(IdempotencyResult.class);
        verify(store).complete(eq(claim()), result.capture());
        assertThat(result.getValue().statusCode()).isEqualTo(201);
        assertThat(result.getValue().headers()).containsEntry("Location", "/orders/42");
        assertThat(new String(result.getValue().body(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("{\"orderId\":42}");
        assertThat(response.getStatus()).isEqualTo(201);
    }

    /**
     * 验证失败 HTTP 状态释放所有权，使客户端可以重试。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldReleaseFailedHttpResponse() throws Exception {
        IdempotencyStore store = acquiredStore();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(store).doFilter(request("request-1"), response, (currentRequest, currentResponse) ->
                ((HttpServletResponse) currentResponse).setStatus(500));

        verify(store).release(claim());
    }

    /**
     * 验证异步请求只有在 AsyncContext 完成后才提交状态。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldHoldClaimUntilAsynchronousRequestCompletes() throws Exception {
        IdempotencyStore store = acquiredStore();
        MockHttpServletRequest request = request("request-1");
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(store).doFilter(request, response, (currentRequest, currentResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) currentResponse;
            httpResponse.setStatus(202);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{}");
            currentRequest.startAsync();
        });
        verify(store, never()).complete(any(), any());

        ((MockAsyncContext) request.getAsyncContext()).complete();

        verify(store).complete(eq(claim()), any(IdempotencyResult.class));
    }

    /**
     * 验证处理中和已完成请求返回不同响应状态提示且不调用业务链。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldDistinguishInProgressAndCompletedRequests() throws Exception {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any()))
                .thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.IN_PROGRESS))
                .thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.COMPLETED));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse inProgress = new MockHttpServletResponse();
        MockHttpServletResponse completed = new MockHttpServletResponse();

        filter(store).doFilter(request("request-1"), inProgress, chain);
        filter(store).doFilter(request("request-1"), completed, chain);

        assertThat(inProgress.getStatus()).isEqualTo(409);
        assertThat(inProgress.getHeader(HttpIdempotencyFilter.IDEMPOTENCY_STATUS_HEADER))
                .isEqualTo("in-progress");
        assertThat(completed.getStatus()).isEqualTo(409);
        assertThat(completed.getHeader(HttpIdempotencyFilter.IDEMPOTENCY_STATUS_HEADER))
                .isEqualTo("completed-not-replayable");
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * 验证已完成请求直接重放原始状态、白名单响应头和响应体。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldReplayCompletedResponseWithoutCallingBusinessChain() throws Exception {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.COMPLETED));
        when(store.findCompletedResult(any())).thenReturn(Optional.of(new IdempotencyResult(
                201,
                "application/json",
                Map.of("Location", "/orders/42"),
                "{\"orderId\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(store).doFilter(request("request-1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader("Location")).isEqualTo("/orders/42");
        assertThat(response.getHeader(HttpIdempotencyFilter.IDEMPOTENCY_STATUS_HEADER)).isEqualTo("replayed");
        assertThat(response.getContentAsString()).isEqualTo("{\"orderId\":42}");
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * 验证不可缓存的内容类型只提交完成状态，不保存响应内容。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldNotCacheDisallowedContentType() throws Exception {
        IdempotencyStore store = acquiredStore();

        filter(store).doFilter(
                request("request-1"),
                new MockHttpServletResponse(),
                (currentRequest, currentResponse) -> {
                    HttpServletResponse httpResponse = (HttpServletResponse) currentResponse;
                    httpResponse.setStatus(200);
                    httpResponse.setContentType("application/octet-stream");
                    httpResponse.getOutputStream().write(new byte[] {1, 2, 3});
                });

        verify(store).complete(claim());
        verify(store, never()).complete(any(), any());
    }

    /**
     * 验证缺少幂等键时返回 400 且不访问状态存储。
     *
     * @throws Exception Filter 执行异常
     */
    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {
        IdempotencyStore store = mock(IdempotencyStore.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(store).doFilter(request(null), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        verify(store, never()).tryStart(any());
    }

    /**
     * 验证业务异常保持为主异常且释放失败作为 suppressed 信息保留。
     */
    @Test
    void shouldPreserveBusinessFailureWhenReleaseFails() {
        IdempotencyStore store = acquiredStore();
        IllegalStateException businessFailure = new IllegalStateException("business failed");
        IllegalArgumentException stateFailure = new IllegalArgumentException("state failed");
        org.mockito.Mockito.doThrow(stateFailure).when(store).release(claim());

        assertThatThrownBy(() -> filter(store).doFilter(
                request("request-1"),
                new MockHttpServletResponse(),
                (currentRequest, currentResponse) -> {
                    throw businessFailure;
                }))
                .isSameAs(businessFailure);
        assertThat(businessFailure.getSuppressed()).containsExactly(stateFailure);
    }

    /**
     * 创建默认 HTTP 幂等 Filter。
     *
     * @param store 幂等状态存储
     * @return HTTP 幂等 Filter
     */
    private HttpIdempotencyFilter filter(IdempotencyStore store) {
        return new HttpIdempotencyFilter(
                store, "create-order", PROCESSING_TIMEOUT, RETENTION);
    }

    /**
     * 创建带可选幂等键的 HTTP 请求。
     *
     * @param key 幂等键
     * @return HTTP 请求
     */
    private MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        if (key != null) {
            request.addHeader(HttpIdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key);
        }
        return request;
    }

    /**
     * 创建返回成功所有权的模拟状态存储。
     *
     * @return 模拟状态存储
     */
    private IdempotencyStore acquiredStore() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.acquired(claim()));
        return store;
    }

    /**
     * 创建测试 HTTP 幂等所有权。
     *
     * @return 幂等所有权
     */
    private IdempotencyClaim claim() {
        return new IdempotencyClaim("create-order", "request-1", "owner", RETENTION);
    }
}
