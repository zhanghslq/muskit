package io.github.zhanghslq.muskit.lock.redis;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.test.lock.FencedDistributedLockProviderContract;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisDistributedLockProvider 的真实 Redis 竞争测试，无 Docker 环境时自动跳过。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class RedisDistributedLockProviderIntegrationTest extends FencedDistributedLockProviderContract {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static RedissonClient firstClient;
    private static RedissonClient secondClient;

    /**
     * 为两个模拟应用实例创建独立 Redisson 客户端。
     */
    @BeforeAll
    static void setUpClients() {
        firstClient = createClient();
        secondClient = createClient();
    }

    /**
     * 关闭集成测试创建的 Redisson 客户端。
     */
    @AfterAll
    static void shutDownClients() {
        if (firstClient != null) {
            firstClient.shutdown();
        }
        if (secondClient != null) {
            secondClient.shutdown();
        }
    }

    /**
     * 创建代表第一个应用实例的 Redis 锁 Provider。
     *
     * @return 第一个 Redis 锁 Provider
     */
    @Override
    protected DistributedLockProvider firstProvider() {
        return new RedisDistributedLockProvider(firstClient, "muskit:test:contract:");
    }

    /**
     * 创建代表第二个应用实例的 Redis 锁 Provider。
     *
     * @return 第二个 Redis 锁 Provider
     */
    @Override
    protected DistributedLockProvider secondProvider() {
        return new RedisDistributedLockProvider(secondClient, "muskit:test:contract:");
    }

    /**
     * 验证两个客户端竞争同一 Redis 锁，并验证异步线程释放后其他客户端可重新获取。
     */
    @Test
    void shouldCoordinateAcrossClientsAndReleaseFromAnotherThread() {
        RedisDistributedLockProvider firstProvider = new RedisDistributedLockProvider(firstClient, "muskit:test:lock:");
        RedisDistributedLockProvider secondProvider = new RedisDistributedLockProvider(secondClient, "muskit:test:lock:");
        DistributedLockRequest request = new DistributedLockRequest(
                "integration", "same-key", Duration.ZERO, Duration.ZERO, false, false);

        DistributedLockHandle firstHandle = acquire(firstProvider, request);
        assertThat(acquireResult(firstProvider, request)).isFalse();
        assertThat(acquireResult(secondProvider, request)).isFalse();

        CompletableFuture.runAsync(firstHandle::close).join();
        DistributedLockHandle secondHandle = acquire(secondProvider, request);
        secondHandle.close();
    }

    /**
     * 创建连接 Testcontainers Redis 的客户端。
     *
     * @return Redisson 客户端
     */
    private static RedissonClient createClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ':' + REDIS.getMappedPort(6379));
        return Redisson.create(config);
    }

    /**
     * 获取测试锁，避免在断言中重复处理中断异常。
     *
     * @param provider Redis 锁提供器
     * @param request 锁请求
     * @return 已获取的锁句柄
     */
    private DistributedLockHandle acquire(
            RedisDistributedLockProvider provider,
            DistributedLockRequest request) {
        try {
            return provider.tryAcquire(request).orElseThrow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("测试线程等待 Redis 锁时被中断", exception);
        }
    }

    /**
     * 返回是否成功获取测试锁。
     *
     * @param provider Redis 锁提供器
     * @param request 锁请求
     * @return 是否获取成功
     */
    private boolean acquireResult(
            RedisDistributedLockProvider provider,
            DistributedLockRequest request) {
        try {
            return provider.tryAcquire(request).isPresent();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("测试线程等待 Redis 锁时被中断", exception);
        }
    }
}
