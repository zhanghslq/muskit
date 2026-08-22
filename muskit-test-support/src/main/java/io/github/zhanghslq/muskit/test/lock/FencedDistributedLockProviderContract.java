package io.github.zhanghslq.muskit.test.lock;

import io.github.zhanghslq.muskit.lock.model.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockProvider;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支持 fencing token 的分布式锁 Provider 契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class FencedDistributedLockProviderContract extends DistributedLockProviderContract {

    /**
     * 创建 fencing 分布式锁契约测试基类。
     */
    protected FencedDistributedLockProviderContract() {
    }

    /**
     * 验证同一业务键每次重新获取的 fencing token 严格递增。
     */
    @Test
    protected final void shouldIncreaseFencingTokenAfterReacquiring() {
        DistributedLockRequest request = new DistributedLockRequest(
                "fencing-contract",
                UUID.randomUUID().toString(),
                Duration.ZERO,
                Duration.ZERO,
                false,
                false,
                true);
        DistributedLockHandle first = acquire(firstProvider(), request);
        long firstToken = first.fencingToken().orElseThrow(
                () -> new AssertionError("要求 fencing 时锁句柄必须返回令牌"));
        first.close();

        DistributedLockHandle second = acquire(secondProvider(), request);
        long secondToken = second.fencingToken().orElseThrow(
                () -> new AssertionError("重新获取 fencing 锁时必须返回令牌"));
        second.close();

        assertTrue(secondToken > firstToken, "同一业务键的 fencing token 必须严格递增");
    }

    /**
     * 获取 fencing 测试锁并恢复中断标记。
     *
     * @param provider 锁 Provider
     * @param request 锁请求
     * @return 已获取的锁句柄
     */
    private DistributedLockHandle acquire(
            DistributedLockProvider provider,
            DistributedLockRequest request) {
        Optional<DistributedLockHandle> handle;
        try {
            handle = provider.tryAcquire(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("契约测试等待 fencing 锁时被中断", exception);
        }
        return handle.orElseThrow(() -> new AssertionError("预期成功获取 fencing 测试锁"));
    }
}
