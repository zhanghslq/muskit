package io.github.zhanghslq.muskit.test.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式锁 Provider 的公共行为契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class DistributedLockProviderContract {

    /**
     * 创建分布式锁 Provider 契约测试基类。
     */
    protected DistributedLockProviderContract() {
    }

    /**
     * 创建代表第一个应用实例的锁 Provider。
     *
     * @return 第一个锁 Provider
     */
    protected abstract DistributedLockProvider firstProvider();

    /**
     * 创建代表第二个应用实例的锁 Provider。
     *
     * @return 第二个锁 Provider
     */
    protected abstract DistributedLockProvider secondProvider();

    /**
     * 验证同一业务键在不同 Provider 之间互斥，释放后可以重新获取。
     */
    @Test
    final void shouldCoordinateAcrossProviders() {
        DistributedLockRequest request = request(uniqueKey());
        DistributedLockHandle first = acquire(firstProvider(), request);

        assertFalse(acquireResult(secondProvider(), request), "第二个 Provider 不应穿透已持有的锁");
        first.close();
        DistributedLockHandle second = acquire(secondProvider(), request);
        second.close();
    }

    /**
     * 验证不同业务键可以分别获取锁。
     */
    @Test
    final void shouldIsolateDifferentBusinessKeys() {
        DistributedLockHandle first = acquire(firstProvider(), request(uniqueKey()));
        DistributedLockHandle second = acquire(secondProvider(), request(uniqueKey()));

        first.close();
        second.close();
    }

    /**
     * 验证锁句柄支持跨线程幂等释放。
     */
    @Test
    final void shouldReleaseIdempotentlyFromAnotherThread() {
        DistributedLockRequest request = request(uniqueKey());
        DistributedLockHandle handle = acquire(firstProvider(), request);

        CompletableFuture.runAsync(handle::close).join();
        handle.close();
        assertTrue(acquireResult(secondProvider(), request), "跨线程释放后应允许其他 Provider 获取锁");
    }

    /**
     * 验证同一 Provider 的独立调用不会被误判为线程重入。
     */
    @Test
    final void shouldNotReenterIndependentInvocations() {
        DistributedLockProvider provider = firstProvider();
        DistributedLockRequest request = request(uniqueKey());
        DistributedLockHandle handle = acquire(provider, request);

        assertFalse(acquireResult(provider, request), "独立调用不应按线程重入处理");
        handle.close();
    }

    /**
     * 创建公共契约使用的锁请求。
     *
     * @param key 唯一业务键
     * @return 锁请求
     */
    private DistributedLockRequest request(String key) {
        return new DistributedLockRequest(
                "provider-contract", key, Duration.ZERO, Duration.ZERO, false, false);
    }

    /**
     * 创建不会写入日志的随机测试业务键。
     *
     * @return 随机业务键
     */
    private String uniqueKey() {
        return UUID.randomUUID().toString();
    }

    /**
     * 获取测试锁并将中断转换为断言错误。
     *
     * @param provider 锁 Provider
     * @param request 锁请求
     * @return 已获取的锁句柄
     */
    private DistributedLockHandle acquire(DistributedLockProvider provider, DistributedLockRequest request) {
        Optional<DistributedLockHandle> handle = tryAcquire(provider, request);
        return handle.orElseThrow(() -> new AssertionError("预期成功获取测试锁"));
    }

    /**
     * 返回测试锁是否获取成功，并立即释放成功获取的锁。
     *
     * @param provider 锁 Provider
     * @param request 锁请求
     * @return 是否获取成功
     */
    private boolean acquireResult(DistributedLockProvider provider, DistributedLockRequest request) {
        Optional<DistributedLockHandle> handle = tryAcquire(provider, request);
        handle.ifPresent(DistributedLockHandle::close);
        return handle.isPresent();
    }

    /**
     * 执行锁获取并将中断转换为断言错误。
     *
     * @param provider 锁 Provider
     * @param request 锁请求
     * @return 锁获取结果
     */
    private Optional<DistributedLockHandle> tryAcquire(
            DistributedLockProvider provider,
            DistributedLockRequest request) {
        try {
            return provider.tryAcquire(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("契约测试等待锁时被中断", exception);
        }
    }
}
