package io.github.zhanghslq.muskit.cache.redis;

import io.github.zhanghslq.muskit.cache.spi.CacheStore;
import io.github.zhanghslq.muskit.test.cache.CacheStoreContract;
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
 * Redis 缓存存储真实实例契约测试，无 Docker 环境时自动跳过。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class RedisCacheStoreIntegrationTest extends CacheStoreContract {

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
     * 返回第一个应用实例的 Redis 缓存存储。
     *
     * @return 第一个缓存存储
     */
    @Override
    protected CacheStore firstStore() {
        return new RedisCacheStore(firstClient, "muskit:test:cache-contract");
    }

    /**
     * 返回第二个应用实例的 Redis 缓存存储。
     *
     * @return 第二个缓存存储
     */
    @Override
    protected CacheStore secondStore() {
        return new RedisCacheStore(secondClient, "muskit:test:cache-contract");
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
