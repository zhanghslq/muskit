package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import java.time.Duration;

import io.github.zhanghslq.muskit.lifecycle.DrainController;
import io.github.zhanghslq.muskit.lifecycle.DrainCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    MuskitLifecycleAutoConfiguration.class,
                    MuskitLifecycleServletAutoConfiguration.class);

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
