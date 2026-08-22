package io.github.zhanghslq.muskit.concurrency.redis;

import io.github.zhanghslq.muskit.concurrency.exception.ConcurrencyBackendException;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 使用 Redis 有租约令牌集合实现跨实例并发上限的额度提供器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RedisConcurrencyLimiter implements ConcurrencyLimiter, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConcurrencyLimiter.class);
    private static final long RETRY_INTERVAL_NANOS = Duration.ofMillis(20).toNanos();
    private static final String ACQUIRE_SCRIPT = """
            local time = redis.call('time')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            redis.call('zremrangebyscore', KEYS[1], '-inf', now)
            if redis.call('zcard', KEYS[1]) < tonumber(ARGV[1]) then
                redis.call('zadd', KEYS[1], now + tonumber(ARGV[2]), ARGV[3])
                redis.call('pexpire', KEYS[1], tonumber(ARGV[2]) * 2)
                return 1
            end
            return 0
            """;
    private static final String RENEW_SCRIPT = """
            if redis.call('zscore', KEYS[1], ARGV[2]) then
                local time = redis.call('time')
                local now = time[1] * 1000 + math.floor(time[2] / 1000)
                redis.call('zadd', KEYS[1], 'XX', now + tonumber(ARGV[1]), ARGV[2])
                redis.call('pexpire', KEYS[1], tonumber(ARGV[1]) * 2)
                return 1
            end
            return 0
            """;
    private static final String RELEASE_SCRIPT = """
            local removed = redis.call('zrem', KEYS[1], ARGV[1])
            if redis.call('zcard', KEYS[1]) == 0 then
                redis.call('del', KEYS[1])
            end
            return removed
            """;

    private final RScript script;
    private final String keyPrefix;
    private final long leaseMillis;
    private final long renewalMillis;
    private final ScheduledExecutorService renewalExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 Redis 分布式并发额度提供器。
     *
     * @param redissonClient Redisson 客户端
     * @param keyPrefix Redis 键前缀
     * @param leaseTime 单个额度的失联租约，正常执行期间会自动续期
     */
    public RedisConcurrencyLimiter(
            RedissonClient redissonClient,
            String keyPrefix,
            Duration leaseTime) {
        Objects.requireNonNull(redissonClient, "RedissonClient 不能为空");
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 并发额度键前缀不能为空");
        }
        Objects.requireNonNull(leaseTime, "Redis 并发额度租约不能为空");
        if (leaseTime.compareTo(Duration.ofMillis(300)) < 0) {
            throw new IllegalArgumentException("Redis 并发额度租约不能小于 300ms");
        }
        this.script = redissonClient.getScript(StringCodec.INSTANCE);
        this.keyPrefix = keyPrefix;
        this.leaseMillis = leaseTime.toMillis();
        this.renewalMillis = Math.max(100, leaseMillis / 3);
        this.renewalExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "muskit-redis-concurrency-renewal");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 在配置等待时间内轮询 Redis 原子脚本并获取跨实例额度。
     *
     * @param request 并发额度请求
     * @return 获取成功时返回带自动续期的额度，否则返回空
     * @throws InterruptedException 等待额度期间线程被中断
     */
    @Override
    public Optional<ConcurrencyPermit> tryAcquire(ConcurrencyRequest request) throws InterruptedException {
        Objects.requireNonNull(request, "并发额度请求不能为空");
        ensureOpen(request.policy().name());
        if (request.policy().fair()) {
            throw new IllegalArgumentException("Redis 分布式并发控制暂不支持公平获取，策略: "
                    + request.policy().name());
        }
        String redisKey = redisKey(request);
        String token = UUID.randomUUID().toString();
        long maxWaitNanos = request.policy().maxWait().toNanos();
        long deadline = saturatingAdd(System.nanoTime(), maxWaitNanos);

        while (true) {
            if (tryAcquireOnce(request, redisKey, token)) {
                RedisPermit permit = new RedisPermit(request.policy().name(), redisKey, token);
                permit.startRenewal();
                return Optional.of(permit);
            }
            long remaining = deadline - System.nanoTime();
            if (maxWaitNanos == 0 || remaining <= 0) {
                return Optional.empty();
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(RETRY_INTERVAL_NANOS, remaining));
        }
    }

    /**
     * 关闭续期线程；仍未释放的额度会在租约到期后由其他实例清理。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            renewalExecutor.shutdownNow();
        }
    }

    /**
     * 执行一次 Redis 原子获取脚本。
     *
     * @param request 并发请求
     * @param redisKey Redis 内部键
     * @param token 唯一额度令牌
     * @return 是否成功获取
     */
    private boolean tryAcquireOnce(ConcurrencyRequest request, String redisKey, String token) {
        try {
            Number result = script.eval(
                    RScript.Mode.READ_WRITE,
                    ACQUIRE_SCRIPT,
                    RScript.ReturnType.LONG,
                    Collections.singletonList(redisKey),
                    request.policy().maxConcurrency(),
                    leaseMillis,
                    token);
            return result.intValue() == 1;
        } catch (RuntimeException exception) {
            throw new ConcurrencyBackendException(request.policy().name(), exception);
        }
    }

    /**
     * 校验 Provider 尚未关闭。
     *
     * @param policyName 并发策略名称
     */
    private void ensureOpen(String policyName) {
        if (closed.get()) {
            throw new IllegalStateException("Redis 并发额度提供器已关闭，策略: " + policyName);
        }
    }

    /**
     * 构造不包含原始业务键的 Redis 内部键。
     *
     * @param request 并发请求
     * @return Redis 内部键
     */
    private String redisKey(ConcurrencyRequest request) {
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

    /**
     * 以饱和方式相加纳秒时间，避免极大等待时间溢出。
     *
     * @param left 左操作数
     * @param right 非负右操作数
     * @return 饱和相加结果
     */
    private long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < left ? Long.MAX_VALUE : result;
    }

    /**
     * Redis 分布式并发额度句柄，负责续期和幂等释放。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private final class RedisPermit implements ConcurrencyPermit {

        private final String policyName;
        private final String redisKey;
        private final String token;
        private final AtomicBoolean permitClosed = new AtomicBoolean();
        private volatile ScheduledFuture<?> renewalFuture;

        /**
         * 创建 Redis 并发额度句柄。
         *
         * @param policyName 并发策略名称
         * @param redisKey Redis 内部键
         * @param token 唯一额度令牌
         */
        private RedisPermit(String policyName, String redisKey, String token) {
            this.policyName = policyName;
            this.redisKey = redisKey;
            this.token = token;
        }

        /**
         * 启动固定周期续期任务。
         */
        private void startRenewal() {
            renewalFuture = renewalExecutor.scheduleAtFixedRate(
                    this::renewSafely, renewalMillis, renewalMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试续期，失败时停止续期并输出不包含业务键的明确告警。
         */
        private void renewSafely() {
            if (permitClosed.get()) {
                return;
            }
            try {
                Number renewed = script.eval(
                        RScript.Mode.READ_WRITE,
                        RENEW_SCRIPT,
                        RScript.ReturnType.LONG,
                        Collections.singletonList(redisKey),
                        leaseMillis,
                        token);
                if (renewed.intValue() != 1) {
                    stopAfterRenewalFailure("额度令牌已失效", null);
                }
            } catch (RuntimeException exception) {
                stopAfterRenewalFailure("Redis 续期调用失败", exception);
            }
        }

        /**
         * 停止失效额度的续期任务并记录策略级告警。
         *
         * @param reason 失败原因
         * @param failure 后端异常，可为空
         */
        private void stopAfterRenewalFailure(String reason, RuntimeException failure) {
            ScheduledFuture<?> currentFuture = renewalFuture;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
            if (failure == null) {
                LOGGER.warn("Redis 分布式并发额度续期停止，策略: {}，原因: {}", policyName, reason);
            } else {
                LOGGER.warn("Redis 分布式并发额度续期停止，策略: {}，原因: {}，异常类型: {}",
                        policyName, reason, failure.getClass().getSimpleName());
            }
        }

        /**
         * 幂等停止续期并从 Redis 原子释放额度。
         */
        @Override
        public void close() {
            if (!permitClosed.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> currentFuture = renewalFuture;
            if (currentFuture != null) {
                currentFuture.cancel(false);
            }
            try {
                script.eval(
                        RScript.Mode.READ_WRITE,
                        RELEASE_SCRIPT,
                        RScript.ReturnType.LONG,
                        Collections.singletonList(redisKey),
                        token);
            } catch (RuntimeException exception) {
                throw new ConcurrencyBackendException(policyName, exception);
            }
        }
    }
}
