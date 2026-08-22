package io.github.zhanghslq.muskit.inbox.service;

import io.github.zhanghslq.muskit.inbox.exception.InboxInProgressException;
import io.github.zhanghslq.muskit.inbox.exception.InboxRetryLaterException;
import io.github.zhanghslq.muskit.inbox.model.InboxAttempt;
import io.github.zhanghslq.muskit.inbox.model.InboxClaim;
import io.github.zhanghslq.muskit.inbox.model.InboxDecision;
import io.github.zhanghslq.muskit.inbox.model.InboxPolicy;
import io.github.zhanghslq.muskit.inbox.model.InboxProcessResult;
import io.github.zhanghslq.muskit.inbox.model.InboxRequest;
import io.github.zhanghslq.muskit.inbox.spi.InboxHandler;
import io.github.zhanghslq.muskit.inbox.spi.InboxStore;
import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.time.Duration;
import java.util.Objects;

/**
 * 驱动 Inbox 竞争、处理、指数退避、死信和人工回放状态机。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxProcessor {

    private static final String BUSINESS_FAILURE = "business-failure";

    private final InboxStore store;
    private final MuskitObservationRegistry observationRegistry;

    /**
     * 使用空观测注册器创建 Inbox 处理器。
     *
     * @param store Inbox 状态存储
     */
    public InboxProcessor(InboxStore store) {
        this(store, MuskitObservationRegistry.noop());
    }

    /**
     * 创建带统一可观测性的 Inbox 处理器。
     *
     * @param store Inbox 状态存储
     * @param observationRegistry 统一观测注册器
     */
    public InboxProcessor(InboxStore store, MuskitObservationRegistry observationRegistry) {
        this.store = Objects.requireNonNull(store, "Inbox 状态存储不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
    }

    /**
     * 按业务消息 ID 执行一次可靠消费，失败时持久化重试或死信状态。
     *
     * @param consumer 低基数消费者名称
     * @param messageId 业务消息唯一标识
     * @param policy Inbox 策略
     * @param handler 业务消息处理器
     * @return 成功、重复或已有死信结果
     * @throws Exception 业务处理异常
     */
    public InboxProcessResult process(
            String consumer,
            String messageId,
            InboxPolicy policy,
            InboxHandler handler) throws Exception {
        Objects.requireNonNull(policy, "Inbox 策略不能为空");
        Objects.requireNonNull(handler, "Inbox 业务处理器不能为空");
        InboxRequest request = new InboxRequest(
                consumer, messageId, policy.processingTimeout(), policy.retention());
        InboxAttempt attempt = store.tryClaim(request);
        ObservationTags tags = ObservationTags.of(MuskitTagKey.POLICY, policy.name());
        if (attempt.decision() == InboxDecision.SUCCEEDED) {
            record(tags, "duplicate");
            return InboxProcessResult.DUPLICATE;
        }
        if (attempt.decision() == InboxDecision.DEAD) {
            record(tags, "dead-skipped");
            return InboxProcessResult.DEAD;
        }
        if (attempt.decision() == InboxDecision.IN_PROGRESS) {
            record(tags, "in-progress");
            throw new InboxInProgressException(policy.name());
        }
        if (attempt.decision() == InboxDecision.RETRY_LATER) {
            record(tags, "retry-later");
            throw new InboxRetryLaterException(policy.name(), attempt.retryAfter());
        }

        InboxClaim claim = attempt.claim().orElseThrow(
                () -> new IllegalStateException("Inbox 存储未返回处理租约"));
        try {
            handler.handle();
            store.complete(claim);
            record(tags, "processed");
            return InboxProcessResult.PROCESSED;
        } catch (Exception businessFailure) {
            handleFailure(claim, policy, businessFailure, tags);
            throw businessFailure;
        }
    }

    /**
     * 人工将指定死信消息恢复为立即可重试状态。
     *
     * @param consumer 消费者名称
     * @param messageId 业务消息 ID
     * @return 是否成功恢复
     */
    public boolean replayDead(String consumer, String messageId) {
        return store.replayDead(consumer, messageId);
    }

    /**
     * 根据当前尝试次数进入指数退避或死信状态，状态写入失败作为 suppressed 保留。
     *
     * @param claim 处理租约
     * @param policy Inbox 策略
     * @param businessFailure 业务异常
     * @param tags 指标标签
     */
    private void handleFailure(
            InboxClaim claim,
            InboxPolicy policy,
            Exception businessFailure,
            ObservationTags tags) {
        try {
            if (claim.attempt() >= policy.maxAttempts()) {
                store.markDead(claim, BUSINESS_FAILURE);
                observationRegistry.increment(
                        MuskitMetric.INBOX_DEAD,
                        tags.and(MuskitTagKey.OUTCOME, "dead"));
            } else {
                store.retry(claim, retryDelay(policy, claim.attempt()), BUSINESS_FAILURE);
                record(tags, "retry-scheduled");
            }
        } catch (RuntimeException stateFailure) {
            businessFailure.addSuppressed(stateFailure);
        }
    }

    /**
     * 按失败次数计算有上限的指数退避。
     *
     * @param policy Inbox 策略
     * @param attempt 当前失败次数
     * @return 重试等待时间
     */
    private Duration retryDelay(InboxPolicy policy, int attempt) {
        long initial = saturatedNanos(policy.initialRetryDelay());
        long maximum = saturatedNanos(policy.maxRetryDelay());
        double calculated = initial * Math.pow(policy.retryMultiplier(), Math.max(0, attempt - 1));
        return Duration.ofNanos((long) Math.min(maximum, calculated));
    }

    /**
     * 将时间转换为饱和纳秒数。
     *
     * @param duration 时间值
     * @return 纳秒值
     */
    private long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 记录一次低基数 Inbox 处理结果。
     *
     * @param tags 基础标签
     * @param outcome 处理结果
     */
    private void record(ObservationTags tags, String outcome) {
        observationRegistry.increment(
                MuskitMetric.INBOX_PROCESS,
                tags.and(MuskitTagKey.OUTCOME, outcome));
    }
}
