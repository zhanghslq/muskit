package io.github.zhanghslq.muskit.lock.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.DistributedLockUnavailableException;
import org.redisson.api.RFuture;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 基于 Redisson 可重入锁实现 Redis 分布式锁。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisDistributedLockProvider implements DistributedLockProvider {

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final AtomicLong ownerIds = new AtomicLong(Long.MAX_VALUE);

    /**
     * 创建 Redis 分布式锁提供器。
     *
     * @param redissonClient Redisson 客户端
     * @param keyPrefix Redis 锁键前缀
     */
    public RedisDistributedLockProvider(RedissonClient redissonClient, String keyPrefix) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "RedissonClient 不能为空");
        Objects.requireNonNull(keyPrefix, "Redis 锁键前缀不能为空");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 锁键前缀不能为空");
        }
        this.keyPrefix = keyPrefix;
    }

    /**
     * 尝试通过 Redis 获取锁，并保留获取线程标识供异步任务跨线程释放。
     *
     * @param request 锁请求
     * @return 获取成功时返回 Redis 锁句柄，否则返回空
     * @throws InterruptedException 等待 Redis 锁期间线程被中断
     */
    @Override
    public Optional<DistributedLockHandle> tryAcquire(DistributedLockRequest request) throws InterruptedException {
        Objects.requireNonNull(request, "锁请求不能为空");
        try {
            String redisKey = buildRedisKey(request);
            RLock lock = request.fair()
                    ? redissonClient.getFairLock(redisKey)
                    : redissonClient.getLock(redisKey);
            // 每次调用使用独立 owner ID，避免入口线程复用时把尚未完成的异步调用误判为可重入调用。
            long ownerThreadId = ownerIds.getAndDecrement();
            long waitMillis = toRedisMillis(request.waitTime());
            long leaseMillis = request.leaseTime().isZero() ? -1 : toRedisMillis(request.leaseTime());
            RFuture<Boolean> acquireFuture = lock.tryLockAsync(
                    waitMillis, leaseMillis, TimeUnit.MILLISECONDS, ownerThreadId);
            boolean acquired;
            try {
                acquired = Boolean.TRUE.equals(acquireFuture.get());
            } catch (InterruptedException exception) {
                // 取消等待后 Redisson 会在竞态获取成功时使用同一 owner ID 自动解锁，避免中断造成锁泄漏。
                acquireFuture.cancel(true);
                throw exception;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new DistributedLockUnavailableException(request.name(), cause);
            }
            if (!acquired) {
                return Optional.empty();
            }
            long fencingToken = request.fencing()
                    ? nextFencingToken(request, redisKey, lock, ownerThreadId)
                    : 0L;
            return Optional.of(new RedisLockHandle(request.name(), lock, ownerThreadId, fencingToken));
        } catch (DistributedLockUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DistributedLockUnavailableException(request.name(), exception);
        }
    }

    /**
     * 组合内部 Redis 锁键，该值不得写入日志或公共异常。
     *
     * @param request 锁请求
     * @return Redis 锁键
     */
    private String buildRedisKey(DistributedLockRequest request) {
        return request.key().isEmpty()
                ? keyPrefix + request.name()
                : keyPrefix + request.name() + ':' + request.key();
    }

    /**
     * 将时间转换为 Redis 使用的毫秒，正数不足一毫秒时向上取整为一毫秒。
     *
     * @param duration 时间长度
     * @return Redis 毫秒数
     */
    private long toRedisMillis(Duration duration) {
        if (duration.isZero()) {
            return 0;
        }
        long millis = duration.toMillis();
        return Math.max(1, millis);
    }

    /**
     * 在已经持有互斥锁后生成严格递增的 fencing token。
     *
     * @param request 锁请求
     * @param redisKey Redis 锁键
     * @param lock 已获取的 Redis 锁
     * @param ownerThreadId Redisson 所有者标识
     * @return 正数 fencing token
     */
    private long nextFencingToken(
            DistributedLockRequest request,
            String redisKey,
            RLock lock,
            long ownerThreadId) {
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(redisKey + ":fencing");
            long token = counter.incrementAndGet();
            if (token <= 0) {
                throw new IllegalStateException("fencing token 计数器已溢出");
            }
            return token;
        } catch (RuntimeException exception) {
            // token 生成失败时不能把一把没有 fencing 语义的锁交给业务，必须先释放锁再报告失败。
            try {
                lock.unlockAsync(ownerThreadId).toCompletableFuture().join();
            } catch (RuntimeException releaseFailure) {
                exception.addSuppressed(releaseFailure);
            }
            throw new DistributedLockUnavailableException(request.name(), exception);
        }
    }

    /**
     * 使用获取锁时的 Redisson 线程标识执行幂等释放。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class RedisLockHandle implements DistributedLockHandle {

        private final String lockName;
        private final RLock lock;
        private final long ownerThreadId;
        private final long fencingToken;
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * 创建 Redis 锁句柄。
         *
         * @param lockName 低基数锁名称
         * @param lock Redisson 锁
         * @param ownerThreadId 获取锁时的线程标识
         * @param fencingToken fencing token，零表示未启用
         */
        private RedisLockHandle(String lockName, RLock lock, long ownerThreadId, long fencingToken) {
            this.lockName = lockName;
            this.lock = lock;
            this.ownerThreadId = ownerThreadId;
            this.fencingToken = fencingToken;
        }

        /**
         * 返回本次锁获取对应的 fencing token。
         *
         * @return 启用 fencing 时返回正数 token，否则为空
         */
        @Override
        public OptionalLong fencingToken() {
            return fencingToken > 0 ? OptionalLong.of(fencingToken) : OptionalLong.empty();
        }

        /**
         * 幂等释放 Redis 锁，固定租约已到期时视为已经释放。
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!lock.isHeldByThread(ownerThreadId)) {
                    return;
                }
                // CompletionStage 可能在其他线程回调，必须显式携带原线程标识才能释放 Redisson 可重入锁。
                lock.unlockAsync(ownerThreadId).toCompletableFuture().join();
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof IllegalMonitorStateException) {
                    return;
                }
                throw new DistributedLockUnavailableException(lockName, exception);
            } catch (IllegalMonitorStateException exception) {
                // 固定租约可能恰好在检查与解锁之间到期，此时锁已经由 Redis 自动释放。
            } catch (RuntimeException exception) {
                throw new DistributedLockUnavailableException(lockName, exception);
            }
        }
    }
}
