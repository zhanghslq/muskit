package io.github.zhanghslq.muskit.concurrency.redis;

import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyScope;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import io.github.zhanghslq.muskit.test.concurrency.DistributedConcurrencyLimiterContract;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Redis 分布式并发额度真实实例测试，无 Docker 环境时自动跳过。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class RedisConcurrencyLimiterIntegrationTest extends DistributedConcurrencyLimiterContract {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static RedissonClient firstClient;
    private static RedissonClient secondClient;
    private RedisConcurrencyLimiter firstLimiter;
    private RedisConcurrencyLimiter secondLimiter;

    /**
     * 创建两个模拟应用实例的 Redisson 客户端。
     */
    @BeforeAll
    static void setUpClients() {
        firstClient = createClient();
        secondClient = createClient();
    }

    /**
     * 关闭两个模拟应用实例的客户端。
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
     * 为每个测试创建共享相同 Redis 命名空间的两个 Provider。
     */
    @BeforeEach
    void setUpLimiters() {
        firstLimiter = limiter(firstClient);
        secondLimiter = limiter(secondClient);
    }

    /**
     * 关闭每个测试创建的额度续期线程。
     */
    @AfterEach
    void shutDownLimiters() {
        if (firstLimiter != null) {
            firstLimiter.close();
        }
        if (secondLimiter != null) {
            secondLimiter.close();
        }
    }

    /**
     * 返回第一个应用实例的 Redis 并发额度 Provider。
     *
     * @return 第一个 Provider
     */
    @Override
    protected RedisConcurrencyLimiter firstLimiter() {
        return firstLimiter;
    }

    /**
     * 返回第二个应用实例的 Redis 并发额度 Provider。
     *
     * @return 第二个 Provider
     */
    @Override
    protected RedisConcurrencyLimiter secondLimiter() {
        return secondLimiter;
    }

    /**
     * 验证两个应用实例共同遵守最大并发数，且额度在短租约期间持续续期。
     *
     * @throws Exception 等待或额度获取失败
     */
    @Test
    void shouldEnforceCapacityAcrossInstancesAndRenewLease() throws Exception {
        ConcurrencyRequest request = distributedRequest();
        ConcurrencyPermit firstPermit = firstLimiter.tryAcquire(request).orElseThrow();
        ConcurrencyPermit secondPermit = secondLimiter.tryAcquire(request).orElseThrow();

        assertThat(firstLimiter.tryAcquire(request)).isEmpty();
        Thread.sleep(700);
        assertThat(secondLimiter.tryAcquire(request)).isEmpty();

        firstPermit.close();
        ConcurrencyPermit replacement = secondLimiter.tryAcquire(request).orElseThrow();
        replacement.close();
        secondPermit.close();
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
     * 创建短租约 Redis 分布式并发 Provider。
     *
     * @param client Redisson 客户端
     * @return Redis 分布式并发 Provider
     */
    private RedisConcurrencyLimiter limiter(RedissonClient client) {
        return new RedisConcurrencyLimiter(client, "muskit:test:distributed:", Duration.ofMillis(300));
    }

    /**
     * 创建允许两个跨实例调用并行的测试策略。
     *
     * @return 测试并发请求
     */
    private ConcurrencyRequest distributedRequest() {
        return new ConcurrencyRequest(
                new ConcurrencyPolicy("distributed", 2, Duration.ZERO, ConcurrencyScope.GLOBAL, false),
                "");
    }
}
