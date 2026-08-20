package io.github.zhanghslq.muskit.concurrency.redis;

import java.time.Duration;
import java.util.List;

import io.github.zhanghslq.muskit.concurrency.ConcurrencyBackendException;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyPermit;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.ConcurrencyScope;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 分布式并发额度提供器单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RedisConcurrencyLimiterTest {

    /**
     * 验证成功获取和幂等释放额度，并确保 Redis 键不包含业务隔离键。
     *
     * @throws InterruptedException 测试线程被中断
     */
    @Test
    void shouldAcquireReleaseAndHashBusinessKey() throws InterruptedException {
        Fixture fixture = fixture();
        doReturn(1L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));

        ConcurrencyPermit permit = fixture.limiter().tryAcquire(request(false)).orElseThrow();
        permit.close();
        permit.close();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> keys = ArgumentCaptor.forClass(List.class);
        verify(fixture.script(), org.mockito.Mockito.times(2)).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                keys.capture(),
                any(Object[].class));
        assertThat(keys.getAllValues().getFirst().getFirst().toString())
                .startsWith("muskit:test:concurrency:tenant-export:")
                .doesNotContain("sensitive-tenant");
        fixture.limiter().close();
    }

    /**
     * 验证 Redis 拒绝后零等待请求立即返回空。
     *
     * @throws InterruptedException 测试线程被中断
     */
    @Test
    void shouldReturnEmptyWhenCapacityIsExhausted() throws InterruptedException {
        Fixture fixture = fixture();
        doReturn(0L).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));

        assertThat(fixture.limiter().tryAcquire(request(false))).isEmpty();
        fixture.limiter().close();
    }

    /**
     * 验证 Redis Provider 明确拒绝无法保证的公平语义。
     */
    @Test
    void shouldRejectUnsupportedFairPolicy() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.limiter().tryAcquire(request(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant-export");
        fixture.limiter().close();
    }

    /**
     * 验证后端异常只暴露低基数策略和异常类型。
     */
    @Test
    void shouldSanitizeBackendFailure() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("contains-sensitive-tenant")).when(fixture.script()).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                anyList(),
                any(Object[].class));

        assertThatThrownBy(() -> fixture.limiter().tryAcquire(request(false)))
                .isInstanceOf(ConcurrencyBackendException.class)
                .hasMessageContaining("tenant-export")
                .hasMessageNotContaining("sensitive-tenant");
        fixture.limiter().close();
    }

    /**
     * 创建 Redis 并发额度测试夹具。
     *
     * @return 测试夹具
     */
    private Fixture fixture() {
        RedissonClient client = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(client.getScript(StringCodec.INSTANCE)).thenReturn(script);
        return new Fixture(script, new RedisConcurrencyLimiter(
                client, "muskit:test:concurrency:", Duration.ofDays(1)));
    }

    /**
     * 创建按业务键隔离的测试请求。
     *
     * @param fair 是否要求公平语义
     * @return 并发额度请求
     */
    private ConcurrencyRequest request(boolean fair) {
        return new ConcurrencyRequest(
                new ConcurrencyPolicy(
                        "tenant-export", 1, Duration.ZERO, ConcurrencyScope.KEY, fair),
                "sensitive-tenant");
    }

    /**
     * 保存模拟脚本和待测 Redis Provider。
     *
     * @param script 模拟 Redis 脚本
     * @param limiter 待测 Provider
     * @author zhs
     * @since 2026-08-20
     */
    private record Fixture(RScript script, RedisConcurrencyLimiter limiter) {
    }
}
