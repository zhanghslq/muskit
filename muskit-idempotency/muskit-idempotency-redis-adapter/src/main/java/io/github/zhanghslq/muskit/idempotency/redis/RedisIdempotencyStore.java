package io.github.zhanghslq.muskit.idempotency.redis;

import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyOwnershipLostException;
import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyStoreException;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyResult;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyResultCodec;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * 使用 Redis Lua 脚本实现原子幂等状态机。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisIdempotencyStore implements IdempotencyStore {

    private static final String CLAIM_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('hset', KEYS[1], 'status', 'PROCESSING', 'owner', ARGV[1])
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            local status = redis.call('hget', KEYS[1], 'status')
            if status == 'COMPLETED' then
                return 3
            end
            return 2
            """;

    private static final String COMPLETE_SCRIPT = """
            if redis.call('hget', KEYS[1], 'status') == 'PROCESSING'
                    and redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
                redis.call('hset', KEYS[1], 'status', 'COMPLETED')
                redis.call('hdel', KEYS[1], 'owner')
                if ARGV[3] == '' then
                    redis.call('hdel', KEYS[1], 'result')
                else
                    redis.call('hset', KEYS[1], 'result', ARGV[3])
                end
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private static final String FIND_RESULT_SCRIPT = """
            if redis.call('hget', KEYS[1], 'status') == 'COMPLETED' then
                return redis.call('hget', KEYS[1], 'result')
            end
            return nil
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('hget', KEYS[1], 'status') == 'PROCESSING'
                    and redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
                redis.call('del', KEYS[1])
                return 1
            end
            return 0
            """;

    private static final String RENEW_SCRIPT = """
            if redis.call('hget', KEYS[1], 'status') == 'PROCESSING'
                    and redis.call('hget', KEYS[1], 'owner') == ARGV[1] then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private final RScript script;
    private final String keyPrefix;

    /**
     * 创建 Redis 幂等状态存储。
     *
     * @param redissonClient Redisson 客户端
     * @param keyPrefix Redis 键前缀
     */
    public RedisIdempotencyStore(RedissonClient redissonClient, String keyPrefix) {
        Objects.requireNonNull(redissonClient, "RedissonClient 不能为空");
        Objects.requireNonNull(keyPrefix, "Redis 幂等键前缀不能为空");
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 幂等键前缀不能为空");
        }
        this.script = redissonClient.getScript(StringCodec.INSTANCE);
        this.keyPrefix = keyPrefix;
    }

    /**
     * 原子创建处理中记录或返回已有状态。
     *
     * @param request 幂等请求
     * @return 幂等尝试结果
     */
    @Override
    public IdempotencyAttempt tryStart(IdempotencyRequest request) {
        Objects.requireNonNull(request, "幂等请求不能为空");
        String ownerToken = UUID.randomUUID().toString();
        try {
            Number result = script.eval(
                    RScript.Mode.READ_WRITE,
                    CLAIM_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey(request.operation(), request.key())),
                    ownerToken,
                    toRedisMillis(request.processingTimeout()));
            return switch (result.intValue()) {
                case 1 -> IdempotencyAttempt.acquired(new IdempotencyClaim(
                        request.operation(), request.key(), ownerToken, request.retention()));
                case 2 -> IdempotencyAttempt.rejected(IdempotencyDecision.IN_PROGRESS);
                case 3 -> IdempotencyAttempt.rejected(IdempotencyDecision.COMPLETED);
                default -> throw new IllegalStateException("Redis 幂等脚本返回未知状态");
            };
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(request.operation(), exception);
        }
    }

    /**
     * 原子将当前所有者的处理中记录转换为成功状态。
     *
     * @param claim 幂等所有权声明
     */
    @Override
    public void complete(IdempotencyClaim claim) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        try {
            Number result = script.eval(
                    RScript.Mode.READ_WRITE,
                    COMPLETE_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey(claim.operation(), claim.key())),
                    claim.ownerToken(),
                    toRedisMillis(claim.retention()),
                    "");
            if (result.intValue() != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 原子将当前所有者的处理中记录转换为成功状态并保存可重放结果。
     *
     * @param claim 幂等所有权声明
     * @param result 可重放结果
     */
    @Override
    public void complete(IdempotencyClaim claim, IdempotencyResult result) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        Objects.requireNonNull(result, "可重放结果不能为空");
        String encoded = Base64.getEncoder().encodeToString(IdempotencyResultCodec.encode(result));
        try {
            Number completed = script.eval(
                    RScript.Mode.READ_WRITE,
                    COMPLETE_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey(claim.operation(), claim.key())),
                    claim.ownerToken(),
                    toRedisMillis(claim.retention()),
                    encoded);
            if (completed.intValue() != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 读取已经完成请求的可重放结果。
     *
     * @param request 幂等请求
     * @return 可重放结果，不存在时返回空
     */
    @Override
    public Optional<IdempotencyResult> findCompletedResult(IdempotencyRequest request) {
        Objects.requireNonNull(request, "幂等请求不能为空");
        try {
            Object encoded = script.eval(
                    RScript.Mode.READ_ONLY,
                    FIND_RESULT_SCRIPT,
                    RScript.ReturnType.STRING,
                    Collections.singletonList(redisKey(request.operation(), request.key())));
            if (encoded == null) {
                return Optional.empty();
            }
            byte[] bytes = Base64.getDecoder().decode(encoded.toString());
            return Optional.of(IdempotencyResultCodec.decode(bytes));
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(request.operation(), exception);
        }
    }

    /**
     * 原子校验所有权并刷新 Redis 处理中状态 TTL。
     *
     * @param claim 幂等所有权声明
     * @param processingTimeout 新处理超时时间
     */
    @Override
    public void renew(IdempotencyClaim claim, Duration processingTimeout) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        Objects.requireNonNull(processingTimeout, "处理超时时间不能为空");
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("处理超时时间必须为正数");
        }
        try {
            Number renewed = script.eval(
                    RScript.Mode.READ_WRITE,
                    RENEW_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey(claim.operation(), claim.key())),
                    claim.ownerToken(),
                    toRedisMillis(processingTimeout));
            if (renewed.intValue() != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 原子删除当前所有者的处理中记录，使失败业务可以重试。
     *
     * @param claim 幂等所有权声明
     */
    @Override
    public void release(IdempotencyClaim claim) {
        Objects.requireNonNull(claim, "幂等所有权声明不能为空");
        try {
            Number result = script.eval(
                    RScript.Mode.READ_WRITE,
                    RELEASE_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey(claim.operation(), claim.key())),
                    claim.ownerToken());
            if (result.intValue() != 1) {
                throw new IdempotencyOwnershipLostException(claim.operation());
            }
        } catch (IdempotencyOwnershipLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IdempotencyStoreException(claim.operation(), exception);
        }
    }

    /**
     * 使用业务键摘要构造 Redis 键，避免原始业务键出现在 Redis Key 中。
     *
     * @param operation 低基数操作名称
     * @param key 业务幂等键
     * @return Redis 键
     */
    private String redisKey(String operation, String key) {
        return keyPrefix + operation + ':' + sha256(key);
    }

    /**
     * 计算业务幂等键的 SHA-256 十六进制摘要。
     *
     * @param value 原始业务幂等键
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

    /**
     * 将正数时间转换为 Redis 毫秒，亚毫秒时间向上取整。
     *
     * @param duration 时间长度
     * @return Redis 毫秒数
     */
    private long toRedisMillis(Duration duration) {
        return Math.max(1, duration.toMillis());
    }
}
