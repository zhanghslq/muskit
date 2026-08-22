package io.github.zhanghslq.muskit.idempotency.redis;

import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import io.github.zhanghslq.muskit.test.idempotency.IdempotencyStoreContract;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 幂等状态存储真实实例契约测试，无 Docker 环境时自动跳过。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class RedisIdempotencyStoreIntegrationTest extends IdempotencyStoreContract {

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
     * 返回第一个 Redis 幂等状态存储。
     *
     * @return 第一个 Redis 幂等状态存储
     */
    @Override
    protected IdempotencyStore firstStore() {
        return new RedisIdempotencyStore(firstClient, "muskit:test:idempotency:");
    }

    /**
     * 返回第二个 Redis 幂等状态存储。
     *
     * @return 第二个 Redis 幂等状态存储
     */
    @Override
    protected IdempotencyStore secondStore() {
        return new RedisIdempotencyStore(secondClient, "muskit:test:idempotency:");
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
}
