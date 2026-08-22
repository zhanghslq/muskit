package io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive;

import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import io.github.zhanghslq.muskit.lifecycle.service.DrainPermit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在 WebFlux 请求真实完成、异常或取消前持有排空许可的响应式过滤器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainWebFilter implements WebFilter, Ordered {

    private static final String REJECTED_BODY = "{\"title\":\"Service is draining\",\"status\":%d}";

    private final DrainController drainController;
    private final int rejectedStatus;
    private final int retryAfterSeconds;
    private final List<String> excludedPathPrefixes;

    /**
     * 创建 WebFlux HTTP 排空过滤器。
     *
     * @param drainController HTTP 请求排空控制器
     * @param rejectedStatus 摘流拒绝状态码
     * @param retryAfterSeconds 建议重试秒数
     * @param excludedPathPrefixes 排空期间放行的路径前缀
     */
    public DrainWebFilter(
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
     * 延迟到订阅时登记在途请求，确保没有订阅的响应式链不会泄漏许可。
     *
     * @param exchange WebFlux 请求交换
     * @param chain 过滤器链
     * @return 请求完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Objects.requireNonNull(exchange, "WebFlux 请求交换不能为空");
        Objects.requireNonNull(chain, "WebFlux 过滤器链不能为空");
        return Mono.defer(() -> filterSubscribed(exchange, chain));
    }

    /**
     * 返回过滤器的高优先级顺序，使排空拒绝尽早发生。
     *
     * @return Spring 过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /**
     * 在单次订阅生命周期内获取并释放排空许可。
     *
     * @param exchange WebFlux 请求交换
     * @param chain 过滤器链
     * @return 请求完成信号
     */
    private Mono<Void> filterSubscribed(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (excludedPathPrefixes.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        DrainPermit permit = drainController.tryEnter().orElse(null);
        if (permit == null) {
            return reject(exchange);
        }
        try {
            // doFinally 同时覆盖成功、异常和客户端取消，保证排空不会因断连永久等待。
            return chain.filter(exchange).doFinally(signalType -> permit.close());
        } catch (RuntimeException | Error failure) {
            // 过滤器链在组装阶段同步失败时不会产生终止信号，需要在当前线程释放许可。
            permit.close();
            throw failure;
        }
    }

    /**
     * 写入不包含请求信息的标准排空拒绝响应。
     *
     * @param exchange WebFlux 请求交换
     * @return 响应写入完成信号
     */
    private Mono<Void> reject(ServerWebExchange exchange) {
        byte[] body = REJECTED_BODY.formatted(rejectedStatus).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(rejectedStatus));
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
