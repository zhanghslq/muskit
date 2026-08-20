package io.github.zhanghslq.muskit.resilience.ratelimit.redis;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicy;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitScope;
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
 * Redis 分布式令牌桶真实实例测试，无 Docker 环境时自动跳过。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class RedisTokenBucketRateLimiterIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private static RedissonClient firstClient;
    private static RedissonClient secondClient;

    /**
     * 创建两个模拟应用实例使用的 Redis 客户端。
     */
    @BeforeAll
    static void setUpClients() {
        firstClient = createClient();
        secondClient = createClient();
    }

    /**
     * 关闭测试 Redis 客户端。
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
     * 验证两个实例共享令牌桶并在服务端时间推进后补充令牌。
     *
     * @throws InterruptedException 等待补充令牌时线程被中断
     */
    @Test
    void shouldShareAndRefillBucketAcrossInstances() throws InterruptedException {
        RedisTokenBucketRateLimiter first = limiter(firstClient);
        RedisTokenBucketRateLimiter second = limiter(secondClient);
        RateLimitRequest request = request("shared-key");

        assertThat(first.tryAcquire(request).allowed()).isTrue();
        assertThat(second.tryAcquire(request).allowed()).isFalse();

        Thread.sleep(1_100);
        assertThat(second.tryAcquire(request).allowed()).isTrue();
    }

    /**
     * 验证不同业务键使用独立令牌桶。
     */
    @Test
    void shouldIsolateDifferentBusinessKeys() {
        RedisTokenBucketRateLimiter limiter = limiter(firstClient);

        assertThat(limiter.tryAcquire(request("key-a")).allowed()).isTrue();
        assertThat(limiter.tryAcquire(request("key-b")).allowed()).isTrue();
    }

    /**
     * 创建测试 Redis 限流器。
     *
     * @param client Redis 客户端
     * @return Redis 限流器
     */
    private RedisTokenBucketRateLimiter limiter(RedissonClient client) {
        return new RedisTokenBucketRateLimiter(client, "muskit:test:rate-limit:");
    }

    /**
     * 创建容量为一的按键限流请求。
     *
     * @param key 业务键
     * @return 限流请求
     */
    private RateLimitRequest request(String key) {
        return new RateLimitRequest(
                new RateLimitPolicy("integration", 1, 1, Duration.ofSeconds(1), RateLimitScope.KEY),
                key);
    }

    /**
     * 创建连接 Testcontainers Redis 的客户端。
     *
     * @return Redis 客户端
     */
    private static RedissonClient createClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ':' + REDIS.getMappedPort(6379));
        return Redisson.create(config);
    }
}
