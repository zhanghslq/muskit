package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive.DrainWebFilter;
import io.github.zhanghslq.muskit.lifecycle.autoconfigure.reactive.MuskitLifecycleReactiveAutoConfiguration;
import io.github.zhanghslq.muskit.lifecycle.autoconfigure.servlet.DrainHttpFilter;
import io.github.zhanghslq.muskit.lifecycle.autoconfigure.servlet.MuskitLifecycleServletAutoConfiguration;
import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import io.github.zhanghslq.muskit.lifecycle.service.DrainCoordinator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生命周期自动配置和 HTTP 摘流测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitLifecycleAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(
                    MuskitLifecycleAutoConfiguration.class,
                    MuskitLifecycleServletAutoConfiguration.class);

    private final ReactiveWebApplicationContextRunner reactiveContextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withUserConfiguration(
                            MuskitLifecycleAutoConfiguration.class,
                            MuskitLifecycleReactiveAutoConfiguration.class);

    /**
     * 验证默认创建排空控制器、协调器、过滤器和生命周期。
     */
    @Test
    void shouldConfigureLifecycleByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DrainController.class);
            assertThat(context).hasSingleBean(DrainCoordinator.class);
            assertThat(context).hasSingleBean(DrainHttpFilter.class);
            assertThat(context).hasSingleBean(MuskitDrainLifecycle.class);
        });
    }

    /**
     * 验证响应式 Web 应用只创建 WebFlux 排空过滤器。
     */
    @Test
    void shouldConfigureReactiveLifecycle() {
        reactiveContextRunner.run(context -> {
            assertThat(context).hasSingleBean(DrainController.class);
            assertThat(context).hasSingleBean(DrainWebFilter.class);
            assertThat(context).doesNotHaveBean(DrainHttpFilter.class);
        });
    }

    /**
     * 验证可以显式关闭生命周期治理。
     */
    @Test
    void shouldDisableLifecycle() {
        contextRunner.withPropertyValues("muskit.lifecycle.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DrainController.class));
    }

    /**
     * 验证排空期间业务请求返回明确的可重试拒绝响应。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldRejectNewHttpRequestWhileDraining() throws Exception {
        DrainController controller = new DrainController("http-requests");
        DrainHttpFilter filter = new DrainHttpFilter(controller, 503, 7, java.util.List.of("/health"));
        controller.beginDrain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("7");
        assertThat(response.getContentAsString()).contains("Service is draining");
        assertThat(controller.awaitDrained(Duration.ZERO)).isTrue();
    }
}
