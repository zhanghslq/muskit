package io.github.zhanghslq.muskit.client.spring;

import io.github.zhanghslq.muskit.client.model.ClientPropagationPolicy;
import io.github.zhanghslq.muskit.client.service.ClientPropagation;
import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spring HTTP 客户端传播拦截器测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitClientHttpRequestInterceptorTest {

    /**
     * 清理测试线程上下文。
     */
    @AfterEach
    void cleanup() {
        MuskitContextHolder.clear();
    }

    /**
     * 验证拦截器覆盖写入白名单请求头并记录调用耗时。
     *
     * @throws Exception 测试调用异常
     */
    @Test
    void shouldPropagateHeadersAndObserveCall() throws Exception {
        ClientPropagation propagation = new ClientPropagation(new ClientPropagationPolicy(
                Duration.ofSeconds(3), Duration.ofSeconds(10), 64, Map.of("tenant", "X-Tenant")));
        MuskitObservationRegistry registry = mock(MuskitObservationRegistry.class);
        MuskitClientHttpRequestInterceptor interceptor =
                new MuskitClientHttpRequestInterceptor(propagation, registry, "remote-service");
        ClientHttpRequest request = mock(ClientHttpRequest.class);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        MuskitContextHolder.set(MuskitContext.of(Map.of("tenant", "t1")));

        ClientHttpResponse actual = interceptor.intercept(request, new byte[0], (ignoredRequest, ignoredBody) -> response);

        assertThat(actual).isSameAs(response);
        assertThat(headers.getFirst("X-Tenant")).isEqualTo("t1");
        assertThat(headers.getFirst(ClientPropagation.DEADLINE_HEADER)).isNotBlank();
        verify(registry).recordDuration(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
