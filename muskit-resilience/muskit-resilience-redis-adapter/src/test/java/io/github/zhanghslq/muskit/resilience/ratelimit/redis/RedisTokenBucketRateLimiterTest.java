package io.github.zhanghslq.muskit.resilience.ratelimit.redis;

import java.time.Duration;
import java.util.List;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitBackendException;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicy;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
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
 * RedisTokenBucketRateLimiter 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RedisTokenBucketRateLimiterTest {

    /**
     * 验证脚本结果会转换为统一允许和拒绝语义。
     */
    @Test
    void shouldMapAtomicScriptResult() {
        Fixture fixture = fixture();
        doReturn(List.of(1L, 0L), List.of(0L, 250L)).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LIST),
                anyList(),
                any(Object[].class));

        assertThat(fixture.limiter().tryAcquire(request("tenant-a")).allowed()).isTrue();
        assertThat(fixture.limiter().tryAcquire(request("tenant-a")).retryAfter())
                .isEqualTo(Duration.ofMillis(250));
    }

    /**
     * 验证 Redis Key 只保存业务键摘要。
     */
    @Test
    void shouldHashBusinessKeyInRedisKey() {
        Fixture fixture = fixture();
        doReturn(List.of(1L, 0L)).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LIST),
                anyList(),
                any(Object[].class));

        fixture.limiter().tryAcquire(request("sensitive-tenant-key"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> keys = ArgumentCaptor.forClass(List.class);
        verify(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LIST),
                keys.capture(),
                any(Object[].class));
        assertThat(keys.getValue().getFirst().toString())
                .startsWith("muskit:rate-limit:tenant-api:")
                .doesNotContain("sensitive-tenant-key");
    }

    /**
     * 验证 Redis 异常不会静默降级为本地限流。
     */
    @Test
    void shouldExposeBackendFailureWithoutBusinessKey() {
        Fixture fixture = fixture();
        when(fixture.script().eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LIST),
                anyList(),
                any(Object[].class))).thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> fixture.limiter().tryAcquire(request("sensitive-tenant-key")))
                .isInstanceOf(RateLimitBackendException.class)
                .hasMessageContaining("tenant-api")
                .hasMessageNotContaining("sensitive-tenant-key");
    }

    /**
     * 创建 Redis 限流器测试夹具。
     *
     * @return 测试夹具
     */
    private Fixture fixture() {
        RedissonClient client = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
        return new Fixture(script, new RedisTokenBucketRateLimiter(client, "muskit:rate-limit:"));
    }

    /**
     * 创建按业务键隔离的测试请求。
     *
     * @param key 业务键
     * @return 限流请求
     */
    private RateLimitRequest request(String key) {
        RateLimitPolicy policy = new RateLimitPolicy(
                "tenant-api", 2, 1, Duration.ofSeconds(1), RateLimitScope.KEY);
        return new RateLimitRequest(policy, key);
    }

    /**
     * 保存模拟脚本和待测限流器。
     *
     * @param script 模拟脚本
     * @param limiter 待测限流器
     * @author zhs
     * @since 2026-08-20
     */
    private record Fixture(RScript script, RedisTokenBucketRateLimiter limiter) {
    }
}
