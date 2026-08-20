package io.github.zhanghslq.muskit.idempotency;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 幂等响应结果编解码测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class IdempotencyResultCodecTest {

    /**
     * 验证状态、内容类型、白名单响应头和二进制响应体可无损往返。
     */
    @Test
    void shouldRoundTripReplayableResult() {
        IdempotencyResult result = new IdempotencyResult(
                201,
                "application/json",
                Map.of("Location", "/orders/42"),
                "{\"orderId\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        IdempotencyResult decoded = IdempotencyResultCodec.decode(IdempotencyResultCodec.encode(result));

        assertThat(decoded.statusCode()).isEqualTo(201);
        assertThat(decoded.contentType()).isEqualTo("application/json");
        assertThat(decoded.headers()).containsEntry("Location", "/orders/42");
        assertThat(decoded.body()).containsExactly(result.body());
    }

    /**
     * 验证结果对象不会暴露可变响应体数组。
     */
    @Test
    void shouldDefensivelyCopyResponseBody() {
        byte[] original = {1, 2, 3};
        IdempotencyResult result = new IdempotencyResult(200, "", Map.of(), original);

        original[0] = 9;
        byte[] returned = result.body();
        returned[1] = 9;

        assertThat(result.body()).containsExactly(1, 2, 3);
    }

    /**
     * 验证损坏的结果编码会明确失败。
     */
    @Test
    void shouldRejectCorruptedEncoding() {
        assertThatThrownBy(() -> IdempotencyResultCodec.decode(new byte[] {0, 0, 0, 1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
