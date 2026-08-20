package io.github.zhanghslq.muskit.idempotency;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等核心值对象和状态判定测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class IdempotencyCoreTest {

    /**
     * 验证请求和所有权描述不会暴露业务键或所有权令牌。
     */
    @Test
    void shouldHideSensitiveValues() {
        IdempotencyRequest request = request("sensitive-key");
        IdempotencyClaim claim = new IdempotencyClaim(
                "payment", "sensitive-key", "secret-token", Duration.ofHours(1));

        assertThat(request.toString()).doesNotContain("sensitive-key");
        assertThat(claim.toString()).doesNotContain("sensitive-key", "secret-token");
    }

    /**
     * 验证只有成功获取判定可以携带所有权声明。
     */
    @Test
    void shouldKeepAttemptAndClaimConsistent() {
        IdempotencyClaim claim = new IdempotencyClaim(
                "payment", "key", "token", Duration.ofHours(1));

        assertThat(IdempotencyAttempt.acquired(claim).claim()).contains(claim);
        assertThat(IdempotencyAttempt.rejected(IdempotencyDecision.IN_PROGRESS).claim()).isEmpty();
        assertThatThrownBy(() -> IdempotencyAttempt.rejected(IdempotencyDecision.ACQUIRED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证幂等请求要求正数超时和保留时间。
     */
    @Test
    void shouldValidateDurations() {
        assertThatThrownBy(() -> new IdempotencyRequest(
                "payment", "key", Duration.ZERO, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处理超时时间必须为正数");
        assertThatThrownBy(() -> new IdempotencyRequest(
                "payment", "key", Duration.ofSeconds(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("成功状态保留时间必须为正数");
    }

    /**
     * 创建测试幂等请求。
     *
     * @param key 业务幂等键
     * @return 测试请求
     */
    private IdempotencyRequest request(String key) {
        return new IdempotencyRequest(
                "payment", key, Duration.ofSeconds(30), Duration.ofHours(1));
    }
}
