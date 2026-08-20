package io.github.zhanghslq.muskit.resilience.ratelimit.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitBackendException;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitDecision;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * 使用 Redis 服务端时间和 Lua 脚本实现的分布式令牌桶限流器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final String ACQUIRE_SCRIPT = """
            local redisTime = redis.call('time')
            local now = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            local capacity = tonumber(ARGV[1])
            local refillTokens = tonumber(ARGV[2])
            local refillPeriod = tonumber(ARGV[3])
            local tokens = tonumber(redis.call('hget', KEYS[1], 'tokens'))
            local lastRefill = tonumber(redis.call('hget', KEYS[1], 'lastRefill'))
            if tokens == nil or lastRefill == nil then
                tokens = capacity
                lastRefill = now
            end
            local elapsed = math.max(0, now - lastRefill)
            tokens = math.min(capacity, tokens + elapsed * refillTokens / refillPeriod)
            local allowed = 0
            local retryAfter = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                retryAfter = math.max(1, math.ceil((1 - tokens) * refillPeriod / refillTokens))
            end
            redis.call('hset', KEYS[1], 'tokens', tostring(tokens), 'lastRefill', tostring(now))
            redis.call('pexpire', KEYS[1], tonumber(ARGV[4]))
            return {allowed, retryAfter}
            """;

    private final RScript script;
    private final String keyPrefix;

    /**
     * 创建 Redis 分布式令牌桶限流器。
     *
     * @param redissonClient Redisson 客户端
     * @param keyPrefix Redis 键前缀
     */
    public RedisTokenBucketRateLimiter(RedissonClient redissonClient, String keyPrefix) {
        Objects.requireNonNull(redissonClient, "RedissonClient 不能为空");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 限流键前缀不能为空");
        }
        this.script = redissonClient.getScript(StringCodec.INSTANCE);
        this.keyPrefix = keyPrefix;
    }

    /**
     * 在 Redis 中原子补充并消耗一个令牌。
     *
     * @param request 限流请求
     * @return 限流判定
     */
    @Override
    public RateLimitDecision tryAcquire(RateLimitRequest request) {
        Objects.requireNonNull(request, "限流请求不能为空");
        long refillPeriodMillis = Math.max(1L, request.policy().refillPeriod().toMillis());
        long idleTtlMillis = idleTtlMillis(request, refillPeriodMillis);
        try {
            List<?> result = script.eval(
                    RScript.Mode.READ_WRITE,
                    ACQUIRE_SCRIPT,
                    RScript.ReturnType.LIST,
                    List.of(redisKey(request)),
                    request.policy().capacity(),
                    request.policy().refillTokens(),
                    refillPeriodMillis,
                    idleTtlMillis);
            if (result == null || result.size() < 2) {
                throw new IllegalStateException("Redis 限流脚本返回结果无效");
            }
            boolean allowed = number(result.get(0)).longValue() == 1L;
            long retryAfterMillis = Math.max(0L, number(result.get(1)).longValue());
            return allowed
                    ? RateLimitDecision.permitted()
                    : RateLimitDecision.rejected(Duration.ofMillis(Math.max(1L, retryAfterMillis)));
        } catch (RuntimeException exception) {
            throw new RateLimitBackendException(request.policy().name(), exception);
        }
    }

    /**
     * 计算一个空桶恢复到满容量所需时间的两倍，作为闲置状态过期时间。
     *
     * @param request 限流请求
     * @param refillPeriodMillis 补充周期毫秒数
     * @return Redis 状态过期毫秒数
     */
    private long idleTtlMillis(RateLimitRequest request, long refillPeriodMillis) {
        double fillPeriods = (double) request.policy().capacity() / request.policy().refillTokens();
        double requested = Math.ceil(fillPeriods * refillPeriodMillis * 2D);
        if (requested >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(refillPeriodMillis, (long) requested);
    }

    /**
     * 将脚本返回值转换为数值。
     *
     * @param value 脚本返回元素
     * @return 数值
     */
    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 构造不包含原始业务键的 Redis 内部键。
     *
     * @param request 限流请求
     * @return Redis 内部键
     */
    private String redisKey(RateLimitRequest request) {
        return keyPrefix + request.policy().name() + ':' + sha256(request.effectiveKey());
    }

    /**
     * 计算业务隔离键的 SHA-256 摘要。
     *
     * @param value 业务隔离键
     * @return 十六进制摘要
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
