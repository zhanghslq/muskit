package io.github.zhanghslq.muskit.lock.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.DistributedLockUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RFuture;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisDistributedLockProvider 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RedisDistributedLockProviderTest {

    /**
     * 验证看门狗锁能够跨线程幂等释放。
     *
     * @throws Exception Redisson 模拟调用失败
     */
    @Test
    void shouldAcquireWatchdogLockAndReleaseIdempotently() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        @SuppressWarnings("unchecked")
        RFuture<Void> unlockFuture = mock(RFuture.class);
        @SuppressWarnings("unchecked")
        RFuture<Boolean> acquireFuture = mock(RFuture.class);
        when(client.getLock("muskit:lock:order-submit:42")).thenReturn(lock);
        when(lock.tryLockAsync(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenReturn(acquireFuture);
        when(acquireFuture.get()).thenReturn(true);
        when(lock.isHeldByThread(anyLong())).thenReturn(true);
        when(lock.unlockAsync(anyLong())).thenReturn(unlockFuture);
        when(unlockFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(null));
        RedisDistributedLockProvider provider = new RedisDistributedLockProvider(client, "muskit:lock:");

        DistributedLockHandle handle = provider.tryAcquire(request(false, Duration.ZERO)).orElseThrow();
        CompletableFuture.runAsync(handle::close).join();
        handle.close();

        ArgumentCaptor<Long> ownerId = ArgumentCaptor.forClass(Long.class);
        verify(lock).tryLockAsync(
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(-1L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS),
                ownerId.capture());
        verify(lock, times(1)).unlockAsync(ownerId.getValue());
    }

    /**
     * 验证公平锁和固定租约属性被传递给 Redisson。
     *
     * @throws Exception Redisson 模拟调用失败
     */
    @Test
    void shouldApplyFairAndLeaseAttributes() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        @SuppressWarnings("unchecked")
        RFuture<Boolean> acquireFuture = mock(RFuture.class);
        when(client.getFairLock("muskit:lock:order-submit:42")).thenReturn(lock);
        when(lock.tryLockAsync(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenReturn(acquireFuture);
        when(acquireFuture.get()).thenReturn(false);
        RedisDistributedLockProvider provider = new RedisDistributedLockProvider(client, "muskit:lock:");

        Optional<DistributedLockHandle> handle = provider.tryAcquire(new DistributedLockRequest(
                "order-submit", "42", Duration.ofMillis(500), Duration.ofSeconds(30), true, false));

        assertThat(handle).isEmpty();
        verify(lock).tryLockAsync(
                org.mockito.ArgumentMatchers.eq(500L),
                org.mockito.ArgumentMatchers.eq(30_000L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS),
                anyLong());
    }

    /**
     * 验证后端异常不会在公共异常链中泄露业务锁键。
     */
    @Test
    void shouldSanitizeBackendFailure() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock("muskit:lock:order-submit:42"))
                .thenThrow(new IllegalStateException("Redis key muskit:lock:order-submit:42 failed"));
        RedisDistributedLockProvider provider = new RedisDistributedLockProvider(client, "muskit:lock:");

        assertThatThrownBy(() -> provider.tryAcquire(request(false, Duration.ZERO)))
                .isInstanceOf(DistributedLockUnavailableException.class)
                .hasMessageContaining("order-submit")
                .hasMessageNotContaining("42")
                .cause()
                .hasMessageNotContaining("42");
    }

    /**
     * 验证等待锁被中断时取消 Redisson 异步请求，避免竞态获取后泄漏锁。
     *
     * @throws Exception Redisson 模拟调用失败
     */
    @Test
    void shouldCancelAcquireFutureWhenInterrupted() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        @SuppressWarnings("unchecked")
        RFuture<Boolean> acquireFuture = mock(RFuture.class);
        when(client.getLock("muskit:lock:order-submit:42")).thenReturn(lock);
        when(lock.tryLockAsync(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenReturn(acquireFuture);
        when(acquireFuture.get()).thenThrow(new InterruptedException("interrupted"));
        RedisDistributedLockProvider provider = new RedisDistributedLockProvider(client, "muskit:lock:");

        assertThatThrownBy(() -> provider.tryAcquire(request(false, Duration.ZERO)))
                .isInstanceOf(InterruptedException.class);
        verify(acquireFuture).cancel(true);
    }

    /**
     * 验证 fencing 模式返回 Redis 原子递增令牌。
     *
     * @throws Exception Redisson 模拟调用失败
     */
    @Test
    void shouldReturnFencingToken() throws Exception {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RAtomicLong counter = mock(RAtomicLong.class);
        @SuppressWarnings("unchecked")
        RFuture<Boolean> acquireFuture = mock(RFuture.class);
        when(client.getLock("muskit:lock:order-submit:42")).thenReturn(lock);
        when(client.getAtomicLong("muskit:lock:order-submit:42:fencing")).thenReturn(counter);
        when(counter.incrementAndGet()).thenReturn(101L);
        when(lock.tryLockAsync(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS), anyLong()))
                .thenReturn(acquireFuture);
        when(acquireFuture.get()).thenReturn(true);
        RedisDistributedLockProvider provider = new RedisDistributedLockProvider(client, "muskit:lock:");

        DistributedLockHandle handle = provider.tryAcquire(new DistributedLockRequest(
                "order-submit", "42", Duration.ZERO, Duration.ZERO, false, false, true)).orElseThrow();

        assertThat(handle.fencingToken()).hasValue(101L);
    }

    /**
     * 创建 Redis 锁测试请求。
     *
     * @param fair 是否公平获取
     * @param leaseTime 租约时间
     * @return 测试锁请求
     */
    private DistributedLockRequest request(boolean fair, Duration leaseTime) {
        return new DistributedLockRequest(
                "order-submit", "42", Duration.ZERO, leaseTime, fair, false);
    }
}
