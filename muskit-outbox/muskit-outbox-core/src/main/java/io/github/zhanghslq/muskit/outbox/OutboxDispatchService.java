package io.github.zhanghslq.muskit.outbox;

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
    private final Duration retryDelay;
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
        this.repository = Objects.requireNonNull(repository, "Outbox 存储不能为空");
        this.publisher = Objects.requireNonNull(publisher, "Outbox 发布器不能为空");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox 批量大小必须大于 0");
        }
        this.batchSize = batchSize;
        this.leaseTime = positive(leaseTime, "Outbox 发布租约必须大于 0");
        this.retryDelay = nonNegative(retryDelay, "Outbox 重试等待时间不能为负数");
    }

    /**
     * 竞争一批事件并逐个发布，普通发布失败会释放事件等待后续重试。
     *
     * @return 批量发布汇总
     */
    public OutboxDispatchReport dispatchBatch() {
        List<OutboxClaim> claims = repository.claimBatch(ownerToken, batchSize, leaseTime);
        int published = 0;
        int failed = 0;
        for (int index = 0; index < claims.size(); index++) {
            OutboxClaim claim = claims.get(index);
            try {
                publisher.publish(claim.event());
                // 消息系统确认后再标记成功；此处数据库失败会导致后续至少一次重复投递。
                repository.markPublished(claim);
                published++;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                releaseAfterInterrupt(claims, index, interrupted);
                throw new OutboxInterruptedException(interrupted);
            } catch (Exception publishFailure) {
                repository.release(claim, retryDelay);
                failed++;
            }
        }
        return new OutboxDispatchReport(claims.size(), published, failed);
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

    /**
     * 校验非负时间配置。
     *
     * @param duration 时间长度
     * @param message 校验消息
     * @return 原时间长度
     */
    private Duration nonNegative(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return duration;
    }
}
