package io.github.zhanghslq.muskit.context;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MuskitContext 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class MuskitContextTest {

    /**
     * 每个测试结束后清理线程上下文。
     */
    @AfterEach
    void cleanContext() {
        MuskitContextHolder.clear();
    }

    /**
     * 验证上下文为不可变值对象。
     */
    @Test
    void shouldCreateImmutableContext() {
        MuskitContext context = MuskitContext.of(Map.of("tenantId", "tenant-1"));

        MuskitContext changed = context.with("operatorId", "operator-1");

        assertThat(context.contains("operatorId")).isFalse();
        assertThat(changed.get("tenantId")).contains("tenant-1");
        assertThat(changed.get("operatorId")).contains("operator-1");
        assertThat(changed.toString()).doesNotContain("tenant-1", "operator-1");
    }

    /**
     * 验证非法上下文键会被拒绝。
     */
    @Test
    void shouldRejectBlankKey() {
        assertThatThrownBy(() -> MuskitContext.of(Map.of(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上下文键");
    }

    /**
     * 验证嵌套作用域关闭时能够逐层恢复上下文。
     */
    @Test
    void shouldRestoreNestedScope() {
        MuskitContext outer = MuskitContext.of(Map.of("tenantId", "outer"));
        MuskitContext inner = MuskitContext.of(Map.of("tenantId", "inner"));

        try (MuskitContextHolder.Scope ignored = MuskitContextHolder.open(outer)) {
            assertThat(MuskitContextHolder.current()).contains(outer);
            try (MuskitContextHolder.Scope nested = MuskitContextHolder.open(inner)) {
                assertThat(MuskitContextHolder.current()).contains(inner);
            }
            assertThat(MuskitContextHolder.current()).contains(outer);
        }

        assertThat(MuskitContextHolder.current()).isEmpty();
    }
}
