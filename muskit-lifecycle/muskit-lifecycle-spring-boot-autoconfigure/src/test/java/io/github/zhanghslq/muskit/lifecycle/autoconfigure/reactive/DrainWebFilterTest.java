package io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive;

import io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive.DrainWebFilter;
import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebFlux 排空过滤器的完成、拒绝和取消语义测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class DrainWebFilterTest {

    /**
     * 验证排空期间新请求收到明确的可重试响应。
     */
    @Test
    void shouldRejectNewReactiveRequestWhileDraining() {
        DrainController controller = new DrainController("http-requests");
        DrainWebFilter filter = filter(controller);
        controller.beginDrain();
        MockServerWebExchange exchange = exchange("/orders");

        filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("7");
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .contains("Service is draining");
    }

    /**
     * 验证响应式请求真实完成前一直计入在途数量。
     *
     * @throws Exception 等待响应式请求完成失败
     */
    @Test
    void shouldHoldPermitUntilReactiveRequestCompletes() throws Exception {
        DrainController controller = new DrainController("http-requests");
        DrainWebFilter filter = filter(controller);
        Sinks.Empty<Void> completion = Sinks.empty();
        CompletableFuture<Void> future = filter
                .filter(exchange("/orders"), ignored -> completion.asMono())
                .toFuture();

        assertThat(controller.snapshot().inflight()).isEqualTo(1L);
        controller.beginDrain();
        assertThat(controller.awaitDrained(Duration.ZERO)).isFalse();

        completion.tryEmitEmpty();
        future.get(5, TimeUnit.SECONDS);

        assertThat(controller.awaitDrained(Duration.ZERO)).isTrue();
    }

    /**
     * 验证客户端取消订阅时释放在途许可，避免排空永久等待。
     */
    @Test
    void shouldReleasePermitWhenClientCancels() {
        DrainController controller = new DrainController("http-requests");
        DrainWebFilter filter = filter(controller);
        Disposable subscription = filter
                .filter(exchange("/orders"), ignored -> Mono.never())
                .subscribe();
        assertThat(controller.snapshot().inflight()).isEqualTo(1L);
        controller.beginDrain();

        subscription.dispose();

        assertThat(controller.awaitDrained(Duration.ofSeconds(1))).isTrue();
    }

    /**
     * 验证排除路径在排空期间仍然交给后续过滤器处理且不计入在途请求。
     */
    @Test
    void shouldBypassExcludedPathWhileDraining() {
        DrainController controller = new DrainController("http-requests");
        DrainWebFilter filter = filter(controller);
        controller.beginDrain();

        filter.filter(exchange("/health/readiness"), ignored -> Mono.empty())
                .block(Duration.ofSeconds(5));

        assertThat(controller.snapshot().inflight()).isZero();
    }

    /**
     * 创建测试 WebFlux 排空过滤器。
     *
     * @param controller 排空控制器
     * @return WebFlux 排空过滤器
     */
    private DrainWebFilter filter(DrainController controller) {
        return new DrainWebFilter(controller, 503, 7, List.of("/health"));
    }

    /**
     * 创建指定路径的模拟 WebFlux 请求交换。
     *
     * @param path 请求路径
     * @return 模拟请求交换
     */
    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
