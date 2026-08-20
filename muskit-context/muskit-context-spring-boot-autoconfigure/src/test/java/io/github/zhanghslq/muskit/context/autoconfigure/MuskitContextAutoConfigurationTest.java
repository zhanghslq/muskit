package io.github.zhanghslq.muskit.context.autoconfigure;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.context.reactor.MuskitReactorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MuskitContextAutoConfiguration 自动配置测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitContextAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MuskitContextAutoConfiguration.class,
                    MuskitContextReactorAutoConfiguration.class));

    /**
     * 每个测试结束后清理当前线程上下文。
     */
    @AfterEach
    void cleanContext() {
        MuskitContextHolder.clear();
    }

    /**
     * 验证默认自动配置会创建访问器、注册器和任务装饰器。
     */
    @Test
    void shouldConfigureContextPropagationByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MuskitContextThreadLocalAccessor.class);
            assertThat(context).hasSingleBean(MuskitContextAccessorRegistrar.class);
            assertThat(context).hasSingleBean(TaskDecorator.class);
            assertThat(context).hasSingleBean(MuskitReactorContextPropagationRegistrar.class);
        });
    }

    /**
     * 验证任务装饰器会在任务执行期间恢复并在执行后清理上下文。
     */
    @Test
    void shouldPropagateContextWithTaskDecorator() {
        contextRunner.run(context -> {
            TaskDecorator decorator = context.getBean(TaskDecorator.class);
            MuskitContext expected = MuskitContext.of(Map.of("tenantId", "tenant-1"));
            AtomicReference<MuskitContext> observed = new AtomicReference<>();
            MuskitContextHolder.set(expected);
            Runnable decorated = decorator.decorate(() -> observed.set(MuskitContextHolder.currentOrEmpty()));
            MuskitContextHolder.clear();

            decorated.run();

            assertThat(observed).hasValue(expected);
            assertThat(MuskitContextHolder.current()).isEmpty();
        });
    }

    /**
     * 验证总开关关闭后不会创建上下文传播组件。
     */
    @Test
    void shouldBackOffWhenDisabled() {
        contextRunner
                .withPropertyValues("muskit.context.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MuskitContextThreadLocalAccessor.class);
                    assertThat(context).doesNotHaveBean(TaskDecorator.class);
                    assertThat(context).doesNotHaveBean(MuskitReactorContextPropagationRegistrar.class);
                });
    }

    /**
     * 验证 Reactor Context 中的业务上下文会在异步调度线程恢复并在信号后清理。
     */
    @Test
    void shouldPropagateContextAcrossReactorScheduler() {
        contextRunner.run(context -> {
            MuskitContext expected = MuskitContext.of(Map.of("tenantId", "tenant-reactive"));
            MuskitContext observed = Mono.defer(() -> Mono.just(MuskitContextHolder.currentOrEmpty()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .contextWrite(MuskitReactorContext.with(expected))
                    .block(Duration.ofSeconds(5));

            assertThat(observed).isEqualTo(expected);
            assertThat(MuskitContextHolder.current()).isEmpty();
        });
    }

    /**
     * 验证可以单独关闭 Reactor 自动传播而保留任务装饰器。
     */
    @Test
    void shouldDisableOnlyReactorPropagation() {
        contextRunner
                .withPropertyValues("muskit.context.reactor-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskDecorator.class);
                    assertThat(context).doesNotHaveBean(MuskitReactorContextPropagationRegistrar.class);
                });
    }
}
