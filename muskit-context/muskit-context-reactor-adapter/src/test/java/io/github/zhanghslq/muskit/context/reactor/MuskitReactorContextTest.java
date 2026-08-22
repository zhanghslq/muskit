package io.github.zhanghslq.muskit.context.reactor;

import io.github.zhanghslq.muskit.context.MuskitContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reactor 业务上下文显式读写测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitReactorContextTest {

    /**
     * 验证业务上下文可以写入并从 Reactor Context 读取。
     */
    @Test
    void shouldWriteAndReadContext() {
        MuskitContext expected = MuskitContext.of(Map.of("tenantId", "tenant-1"));

        Context reactorContext = MuskitReactorContext.with(expected).apply(Context.empty());

        assertThat(MuskitReactorContext.find(reactorContext)).contains(expected);
    }

    /**
     * 验证写入空业务上下文会清除已有值。
     */
    @Test
    void shouldRemoveContextWhenWritingEmptyValue() {
        MuskitContext existing = MuskitContext.of(Map.of("tenantId", "tenant-1"));
        Context reactorContext = Context.of(
                io.github.zhanghslq.muskit.context.MuskitContextHolder.CONTEXT_KEY,
                existing);

        Context cleared = MuskitReactorContext.with(MuskitContext.empty()).apply(reactorContext);

        assertThat(MuskitReactorContext.find(cleared)).isEmpty();
    }
}
