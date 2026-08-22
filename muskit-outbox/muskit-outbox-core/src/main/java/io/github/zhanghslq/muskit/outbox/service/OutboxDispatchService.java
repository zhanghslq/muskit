package io.github.zhanghslq.muskit.outbox.service;

import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.outbox.exception.OutboxDeadLetterNotificationException;
import io.github.zhanghslq.muskit.outbox.exception.OutboxInterruptedException;
import io.github.zhanghslq.muskit.outbox.model.OutboxClaim;
import io.github.zhanghslq.muskit.outbox.model.OutboxDispatchReport;
import io.github.zhanghslq.muskit.outbox.model.OutboxRetryPolicy;
import io.github.zhanghslq.muskit.outbox.spi.OutboxDeadLetterSink;
import io.github.zhanghslq.muskit.outbox.spi.OutboxPublisher;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 通过有期限租约批量执行至少一次 Outbox 发布。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxDispatchService {

    private final OutboxRepository repository;
    private final OutboxPublisher publisher;
    private final int batchSize;
    private final Duration leaseTime;
    private final OutboxRetryPolicy retryPolicy;
    private final MuskitObservationRegistry observationRegistry;
    private final OutboxDeadLetterSink deadLetterSink;
    private final String ownerToken = UUID.randomUUID().toString();

    /**
     * 创建 Outbox 批量发布服务。
     *
     * @param repository Outbox 存储
     * @param publisher 消息发布器
     * @param batchSize 单批最大事件数
     * @param leaseTime 单个发布租约时间
     * @param retryDelay 发布失败后重试等待时间
     */
    public OutboxDispatchService(
            OutboxRepository repository,
            OutboxPublisher publisher,
            int batchSize,
            Duration leaseTime,
            Duration retryDelay) {
        this(repository, publisher, batchSize, leaseTime, retryDelay, MuskitObservationRegistry.noop());
    }

    /**
     * 创建带统一可观测性的 Outbox 批量发布服务。
     *
     * @param repository Outbox 存储
     * @param publisher 消息发布器
     * @param batchSize 单批最大事件数
     * @param leaseTime 单个发布租约时间
     * @param retryDelay 发布失败后重试等待时间
     * @param observationRegistry 统一观测注册器
     */
    public OutboxDispatchService(
            OutboxRepository repository,
            OutboxPublisher publisher,
            int batchSize,
            Duration leaseTime,
            Duration retryDelay,
            MuskitObservationRegistry observationRegistry) {
        this(
                repository,
                publisher,
                batchSize,
                leaseTime,
                new OutboxRetryPolicy(Integer.MAX_VALUE, retryDelay, 1D, retryDelay),
                observationRegistry,
                OutboxDeadLetterSink.noop());
    }

    /**
     * 创建支持指数退避、死信和外部死信通知的 Outbox 发布服务。
     *
     * @param repository Outbox 存储
     * @param publisher 消息发布器
     * @param batchSize 单批最大事件数
     * @param leaseTime 发布租约时间
     * @param retryPolicy 重试和死信策略
     * @param observationRegistry 统一观测注册器
     * @param deadLetterSink 外部死信通知 SPI
     */
    public OutboxDispatchService(
            OutboxRepository repository,
            OutboxPublisher publisher,
            int batchSize,
            Duration leaseTime,
            OutboxRetryPolicy retryPolicy,
            MuskitObservationRegistry observationRegistry,
            OutboxDeadLetterSink deadLetterSink) {
        this.repository = Objects.requireNonNull(repository, "Outbox 存储不能为空");
        this.publisher = Objects.requireNonNull(publisher, "Outbox 发布器不能为空");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox 批量大小必须大于 0");
        }
        this.batchSize = batchSize;
        this.leaseTime = positive(leaseTime, "Outbox 发布租约必须大于 0");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "Outbox 重试策略不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        this.deadLetterSink = Objects.requireNonNull(deadLetterSink, "Outbox 死信通知 SPI 不能为空");
    }

    /**
     * 竞争一批事件并逐个发布，普通发布失败会释放事件等待后续重试。
     *
     * @return 批量发布汇总
     */
    public OutboxDispatchReport dispatchBatch() {
        updatePendingGauge();
        List<OutboxClaim> claims = repository.claimBatch(ownerToken, batchSize, leaseTime);
        int published = 0;
        int failed = 0;
        int dead = 0;
        for (int index = 0; index < claims.size(); index++) {
            OutboxClaim claim = claims.get(index);
            try {
                publisher.publish(claim.event());
                // 消息系统确认后再标记成功；此处数据库失败会导致后续至少一次重复投递。
                repository.markPublished(claim);
                published++;
                observationRegistry.increment(
                        MuskitMetric.OUTBOX_PUBLISH,
                        ObservationTags.of(MuskitTagKey.OUTCOME, "published"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                releaseAfterInterrupt(claims, index, interrupted);
                throw new OutboxInterruptedException(interrupted);
            } catch (Exception publishFailure) {
                failed++;
                observationRegistry.increment(
                        MuskitMetric.OUTBOX_PUBLISH,
                        ObservationTags.of(MuskitTagKey.OUTCOME, "failed"));
                if (claim.attempt() >= retryPolicy.maxAttempts()) {
                    repository.markDead(claim, "publish-failure");
                    dead++;
                    observationRegistry.increment(
                            MuskitMetric.OUTBOX_DEAD,
                            ObservationTags.of(MuskitTagKey.OUTCOME, "dead"));
                    notifyDeadLetter(claim, publishFailure);
                } else {
                    repository.release(claim, retryPolicy.delayAfterFailure(claim.attempt()));
                    observationRegistry.increment(
                            MuskitMetric.OUTBOX_RETRY,
                            ObservationTags.of(MuskitTagKey.OUTCOME, "scheduled"));
                }
            }
        }
        return new OutboxDispatchReport(claims.size(), published, failed, dead);
    }

    /**
     * 在死信状态已经持久化后通知外部 Sink，通知失败不会把事件恢复为可发布状态。
     *
     * @param claim 死信事件租约
     * @param publishFailure 原发布异常
     */
    private void notifyDeadLetter(OutboxClaim claim, Exception publishFailure) {
        try {
            deadLetterSink.publish(claim);
        } catch (Exception notificationFailure) {
            notificationFailure.addSuppressed(publishFailure);
            throw new OutboxDeadLetterNotificationException(notificationFailure);
        }
    }

    /**
     * 在存储支持积压统计时更新待发布仪表，不支持时不创建误导性的零值指标。
     */
    private void updatePendingGauge() {
        long pending = repository.countPending();
        if (pending >= 0L) {
            observationRegistry.setGauge(
                    MuskitMetric.OUTBOX_PENDING,
                    pending,
                    ObservationTags.of(MuskitTagKey.STATE, "pending"));
        }
    }

    /**
     * 中断后释放当前及尚未处理的租约，避免必须等待租约自然到期。
     *
     * @param claims 当前批次租约
     * @param currentIndex 当前处理位置
     * @param interrupted 中断异常
     */
    private void releaseAfterInterrupt(
            List<OutboxClaim> claims,
            int currentIndex,
            InterruptedException interrupted) {
        for (int index = currentIndex; index < claims.size(); index++) {
            try {
                repository.release(claims.get(index), Duration.ZERO);
            } catch (RuntimeException releaseFailure) {
                interrupted.addSuppressed(releaseFailure);
            }
        }
    }

    /**
     * 校验正数时间配置。
     *
     * @param duration 时间长度
     * @param message 校验消息
     * @return 原时间长度
     */
    private Duration positive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return duration;
    }

}
