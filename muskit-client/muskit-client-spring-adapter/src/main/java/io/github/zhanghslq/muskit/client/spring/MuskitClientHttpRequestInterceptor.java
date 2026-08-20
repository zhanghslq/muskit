package io.github.zhanghslq.muskit.client.spring;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import io.github.zhanghslq.muskit.client.ClientPropagation;
import io.github.zhanghslq.muskit.observation.MuskitMetric;
import io.github.zhanghslq.muskit.observation.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.observation.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.ObservationTags;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 为 Spring 同步 HTTP 客户端传播调用链上下文并记录低基数调用指标。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ClientPropagation propagation;
    private final MuskitObservationRegistry observationRegistry;
    private final String operation;

    /**
     * 创建同步 HTTP 客户端拦截器。
     *
     * @param propagation 调用链传播器
     * @param observationRegistry 统一观测注册器
     * @param operation 稳定的低基数操作名称
     */
    public MuskitClientHttpRequestInterceptor(
            ClientPropagation propagation,
            MuskitObservationRegistry observationRegistry,
            String operation) {
        this.propagation = Objects.requireNonNull(propagation, "调用链传播器不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        if (operation == null || operation.isBlank() || operation.length() > 128) {
            throw new IllegalArgumentException("客户端操作名称不能为空且长度不能超过 128");
        }
        this.operation = operation;
    }

    /**
     * 覆盖写入受控请求头并执行 HTTP 调用。
     *
     * @param request HTTP 请求
     * @param body 请求体
     * @param execution 请求执行链
     * @return HTTP 响应
     * @throws IOException 网络调用失败
     */
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        Map<String, String> headers = propagation.outboundHeaders();
        // 必须覆盖调用方手工设置的传播头，防止旧预算或伪造上下文逃逸白名单控制。
        headers.forEach(request.getHeaders()::set);
        long startedAt = System.nanoTime();
        String outcome = "io_error";
        try {
            ClientHttpResponse response = execution.execute(request, body);
            outcome = statusOutcome(response);
            return response;
        } catch (RuntimeException exception) {
            outcome = "runtime_error";
            throw exception;
        } finally {
            observationRegistry.recordDuration(
                    MuskitMetric.CLIENT_CALL,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt)),
                    tags(outcome));
        }
    }

    /**
     * 将响应状态归一化为有限结果标签。
     *
     * @param response HTTP 响应
     * @return 有限结果标签
     * @throws IOException 读取响应状态失败
     */
    private String statusOutcome(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status >= 200 && status < 300) {
            return "success";
        }
        if (status >= 400 && status < 500) {
            return "client_error";
        }
        if (status >= 500) {
            return "server_error";
        }
        return "other";
    }

    /**
     * 创建客户端调用低基数标签。
     *
     * @param outcome 调用结果
     * @return 指标标签
     */
    private ObservationTags tags(String outcome) {
        return ObservationTags.of(MuskitTagKey.OPERATION, operation)
                .and(MuskitTagKey.PROVIDER, "spring-rest-client")
                .and(MuskitTagKey.OUTCOME, outcome);
    }
}
