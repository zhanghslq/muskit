package io.github.zhanghslq.muskit.idempotency.redis;

import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyResult;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyResultCodec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisIdempotencyStore 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RedisIdempotencyStoreTest {

    /**
     * 验证 Redis 脚本结果被转换为统一幂等状态。
     */
    @Test
    void shouldMapAtomicClaimResults() {
        Fixture fixture = fixture();
        doReturn(1L, 2L, 3L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));

        assertThat(fixture.store().tryStart(request("A")).decision()).isEqualTo(IdempotencyDecision.ACQUIRED);
        assertThat(fixture.store().tryStart(request("A")).decision()).isEqualTo(IdempotencyDecision.IN_PROGRESS);
        assertThat(fixture.store().tryStart(request("A")).decision()).isEqualTo(IdempotencyDecision.COMPLETED);
    }

    /**
     * 验证 Redis Key 只包含业务幂等键摘要。
     */
    @Test
    void shouldHashBusinessKeyInRedisKey() {
        Fixture fixture = fixture();
        doReturn(2L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));

        fixture.store().tryStart(request("sensitive-business-key"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> keys = ArgumentCaptor.forClass(List.class);
        verify(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                keys.capture(),
                any(Object[].class));
        assertThat(keys.getValue().getFirst().toString())
                .startsWith("muskit:idempotency:payment:")
                .doesNotContain("sensitive-business-key");
    }

    /**
     * 验证完成记录时所有权不匹配会明确失败。
     */
    @Test
    void shouldRejectCompletionAfterOwnershipIsLost() {
        Fixture fixture = fixture();
        doReturn(0L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));
        IdempotencyClaim claim = new IdempotencyClaim(
                "payment", "key", "owner", Duration.ofHours(1));

        assertThatThrownBy(() -> fixture.store().complete(claim))
                .isInstanceOf(IdempotencyOwnershipLostException.class)
                .hasMessageContaining("payment")
                .hasMessageNotContaining("key")
                .hasMessageNotContaining("owner");
    }

    /**
     * 验证可重放结果会编码写入 Redis 并可以解码读取。
     */
    @Test
    void shouldStoreAndLoadReplayableResult() {
        Fixture fixture = fixture();
        IdempotencyResult expected = new IdempotencyResult(
                200, "application/json", Map.of("ETag", "v1"), new byte[] {1, 2, 3});
        doReturn(1L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));
        doReturn(Base64.getEncoder().encodeToString(IdempotencyResultCodec.encode(expected)))
                .when(fixture.script()).eval(
                        eq(RScript.Mode.READ_ONLY),
                        anyString(),
                        eq(RScript.ReturnType.STRING),
                        anyList(),
                        any(Object[].class));
        IdempotencyClaim claim = new IdempotencyClaim(
                "payment", "key", "owner", Duration.ofHours(1));

        fixture.store().complete(claim, expected);

        IdempotencyResult loaded = fixture.store().findCompletedResult(request("key")).orElseThrow();
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertThat(loaded.headers()).containsEntry("ETag", "v1");
        assertThat(loaded.body()).containsExactly(1, 2, 3);
    }

    /**
     * 创建 Redis 幂等存储测试夹具。
     *
     * @return 测试夹具
     */
    private Fixture fixture() {
        RedissonClient client = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
        return new Fixture(script, new RedisIdempotencyStore(client, "muskit:idempotency:"));
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

    /**
     * 保存模拟 Redis 脚本和待测存储。
     *
     * @param script 模拟脚本
     * @param store 待测存储
     * @author zhs
     * @since 2026-08-20
     */
    private record Fixture(RScript script, RedisIdempotencyStore store) {
    }
}
