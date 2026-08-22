package io.github.zhanghslq.muskit.test.concurrency;

import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyScope;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有并发额度 Provider 都应通过的基础行为契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class ConcurrencyLimiterContract {

    /**
     * 创建并发额度 Provider 契约测试基类。
     */
    protected ConcurrencyLimiterContract() {
    }

    /**
     * 返回待验证的并发额度 Provider。
     *
     * @return 并发额度 Provider
     */
    protected abstract ConcurrencyLimiter limiter();

    /**
     * 验证额度耗尽后拒绝新调用，释放后可以重新获取。
     */
    @Test
    protected final void shouldRejectAtCapacityAndReacquireAfterRelease() {
        ConcurrencyRequest request = request(ConcurrencyScope.GLOBAL, "", 1);
        ConcurrencyPermit first = acquire(limiter(), request);

        assertFalse(acquireResult(limiter(), request), "额度耗尽后不应允许新的调用进入");
        first.close();
        assertTrue(acquireResult(limiter(), request), "释放额度后应允许新的调用进入");
    }

    /**
     * 验证按键策略不会让不同业务键互相占用额度。
     */
    @Test
    protected final void shouldIsolateCapacityByBusinessKey() {
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();
        ConcurrencyRequest firstRequest = request(ConcurrencyScope.KEY, firstKey, 1);
        ConcurrencyRequest secondRequest = new ConcurrencyRequest(firstRequest.policy(), secondKey);
        ConcurrencyPermit first = acquire(limiter(), firstRequest);

        assertFalse(acquireResult(limiter(), firstRequest), "同一业务键不应超过并发上限");
        assertTrue(acquireResult(limiter(), secondRequest), "不同业务键应拥有独立额度");
        first.close();
    }

    /**
     * 验证额度句柄可以跨线程幂等释放。
     */
    @Test
    protected final void shouldReleaseIdempotentlyFromAnotherThread() {
        ConcurrencyRequest request = request(ConcurrencyScope.GLOBAL, "", 1);
        ConcurrencyPermit permit = acquire(limiter(), request);

        CompletableFuture.runAsync(permit::close).orTimeout(5, TimeUnit.SECONDS).join();
        permit.close();

        assertTrue(acquireResult(limiter(), request), "跨线程释放后应恢复可用额度");
    }

    /**
     * 创建互不污染的测试请求。
     *
     * @param scope 并发隔离范围
     * @param key 业务键
     * @param maxConcurrency 最大并发数
     * @return 并发额度请求
     */
    protected final ConcurrencyRequest request(
            ConcurrencyScope scope,
            String key,
            int maxConcurrency) {
        ConcurrencyPolicy policy = new ConcurrencyPolicy(
                "provider-contract-" + UUID.randomUUID(),
                maxConcurrency,
                Duration.ZERO,
                scope,
                false);
        return new ConcurrencyRequest(policy, key);
    }

    /**
     * 获取测试额度并把中断转换为断言错误。
     *
     * @param provider 并发额度 Provider
     * @param request 并发额度请求
     * @return 已获取的额度句柄
     */
    protected final ConcurrencyPermit acquire(
            ConcurrencyLimiter provider,
            ConcurrencyRequest request) {
        return tryAcquire(provider, request)
                .orElseThrow(() -> new AssertionError("预期成功获取测试并发额度"));
    }

    /**
     * 返回额度是否获取成功，并立即释放成功获取的额度。
     *
     * @param provider 并发额度 Provider
     * @param request 并发额度请求
     * @return 是否获取成功
     */
    protected final boolean acquireResult(
            ConcurrencyLimiter provider,
            ConcurrencyRequest request) {
        Optional<ConcurrencyPermit> permit = tryAcquire(provider, request);
        permit.ifPresent(ConcurrencyPermit::close);
        return permit.isPresent();
    }

    /**
     * 尝试获取额度并恢复测试线程的中断标记。
     *
     * @param provider 并发额度 Provider
     * @param request 并发额度请求
     * @return 获取结果
     */
    private Optional<ConcurrencyPermit> tryAcquire(
            ConcurrencyLimiter provider,
            ConcurrencyRequest request) {
        try {
            return provider.tryAcquire(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("契约测试等待并发额度时被中断", exception);
        }
    }
}
