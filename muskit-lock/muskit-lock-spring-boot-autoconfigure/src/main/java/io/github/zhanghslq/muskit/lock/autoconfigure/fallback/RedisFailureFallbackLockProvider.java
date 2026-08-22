package io.github.zhanghslq.muskit.lock.autoconfigure.fallback;

import io.github.zhanghslq.muskit.lock.autoconfigure.observation.LockObservation;
import io.github.zhanghslq.muskit.lock.exception.DistributedLockUnavailableException;
import io.github.zhanghslq.muskit.lock.exception.FencingTokenUnavailableException;
import io.github.zhanghslq.muskit.lock.model.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.spi.DistributedLockProvider;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 在业务显式允许时将 Redis 获取异常降级为当前 JVM 的本地锁。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisFailureFallbackLockProvider implements DistributedLockProvider {

    private static final Log LOGGER = LogFactory.getLog(RedisFailureFallbackLockProvider.class);

    private final DistributedLockProvider redisProvider;
    private final DistributedLockProvider localProvider;
    private final LockObservation lockObservation;

    /**
     * 创建 Redis 优先的锁提供器。
     *
     * @param redisProvider Redis 锁提供器
     * @param localProvider 本地降级锁提供器
     * @param lockObservation 锁指标记录器
     */
    public RedisFailureFallbackLockProvider(
            DistributedLockProvider redisProvider,
            DistributedLockProvider localProvider,
            LockObservation lockObservation) {
        this.redisProvider = Objects.requireNonNull(redisProvider, "Redis 锁提供器不能为空");
        this.localProvider = Objects.requireNonNull(localProvider, "本地锁提供器不能为空");
        this.lockObservation = Objects.requireNonNull(lockObservation, "锁指标记录器不能为空");
    }

    /**
     * 优先获取 Redis 锁，仅在 Redis 异常且请求显式允许时降级。
     *
     * @param request 锁请求
     * @return 获取成功时返回锁句柄，否则返回空
     * @throws InterruptedException 等待锁期间线程被中断
     */
    @Override
    public Optional<DistributedLockHandle> tryAcquire(DistributedLockRequest request) throws InterruptedException {
        try {
            return redisProvider.tryAcquire(request);
        } catch (DistributedLockUnavailableException exception) {
            if (!request.localFallback()) {
                throw exception;
            }
            if (request.fencing()) {
                LOGGER.warn("Redis 分布式锁不可用，fencing 模式禁止降级为本地锁，lock=" + request.name());
                throw new FencingTokenUnavailableException(request.name(), exception);
            }
            LOGGER.warn("Redis 分布式锁不可用，已降级为仅当前 JVM 生效的本地锁，"
                    + "跨实例互斥不再保证，lock=" + request.name());
            lockObservation.fallback(request.name());
            return localProvider.tryAcquire(request);
        }
    }
}
