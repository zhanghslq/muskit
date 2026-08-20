package io.github.zhanghslq.muskit.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import io.github.zhanghslq.muskit.context.MuskitContext;
import io.github.zhanghslq.muskit.context.MuskitContextHolder;
import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 调用链请求头传播测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class ClientPropagationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /**
     * 清理测试线程上下文。
     */
    @AfterEach
    void cleanup() {
        MuskitContextHolder.clear();
    }

    /**
     * 验证出站传播只包含白名单上下文并收紧父 Deadline。
     */
    @Test
    void shouldPropagateWhitelistAndNarrowDeadline() {
        ClientPropagation propagation = propagation();
        MuskitContext context = MuskitContext.of(Map.of("tenant", "t1", "secret", "hidden"));
        try (MuskitContextHolder.Scope ignored = MuskitContextHolder.open(context);
                DeadlineContext.Scope ignoredDeadline = DeadlineContext.open(Deadline.after(Duration.ofSeconds(20), CLOCK))) {
            Map<String, String> headers = propagation.outboundHeaders();

            assertThat(headers).containsEntry("X-Tenant", "t1")
                    .containsEntry(ClientPropagation.DEADLINE_HEADER, Long.toString(NOW.plusSeconds(5).toEpochMilli()))
                    .doesNotContainValue("hidden");
        }
    }

    /**
     * 验证入站作用域合并并在关闭后恢复原上下文。
     */
    @Test
    void shouldOpenAndRestoreInboundScope() {
        ClientPropagation propagation = propagation();
        MuskitContextHolder.set(MuskitContext.of(Map.of("local", "kept")));
        Map<String, String> headers = Map.of(
                "X-Tenant", "t2",
                ClientPropagation.DEADLINE_HEADER, Long.toString(NOW.plusSeconds(30).toEpochMilli()));

        try (ClientPropagation.InboundScope ignored = propagation.openInbound(headers::get)) {
            assertThat(MuskitContextHolder.currentOrEmpty().values())
                    .containsEntry("local", "kept")
                    .containsEntry("tenant", "t2");
            assertThat(DeadlineContext.current()).isPresent();
            assertThat(DeadlineContext.current().orElseThrow().remaining()).isEqualTo(Duration.ofSeconds(10));
        }

        assertThat(MuskitContextHolder.currentOrEmpty().values()).containsOnly(Map.entry("local", "kept"));
        assertThat(DeadlineContext.current()).isEmpty();
    }

    /**
     * 验证包含换行符的上下文请求头会被拒绝。
     */
    @Test
    void shouldRejectHeaderInjection() {
        assertThatThrownBy(() -> propagation().openInbound(
                name -> name.equals("X-Tenant") ? "safe\r\ninjected" : null))
                .isInstanceOf(InvalidPropagationHeaderException.class)
                .hasMessageNotContaining("injected");
    }

    /**
     * 创建固定时钟测试传播器。
     *
     * @return 测试传播器
     */
    private ClientPropagation propagation() {
        return new ClientPropagation(
                new ClientPropagationPolicy(Duration.ofSeconds(5), Duration.ofSeconds(10), 32,
                        Map.of("tenant", "X-Tenant")),
                CLOCK);
    }
}
