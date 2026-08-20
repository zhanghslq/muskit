package io.github.zhanghslq.muskit.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 事务存储和发布租约 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface OutboxRepository {

    /**
     * 在调用方当前业务事务中追加事件。
     *
     * @param event Outbox 事件
     */
    void append(OutboxEvent event);

    /**
     * 原子竞争一批可发布或租约过期的事件。
     *
     * @param ownerToken 发布实例令牌
     * @param batchSize 最大批量大小
     * @param leaseTime 发布租约时间
     * @return 当前实例取得的事件租约
     */
    List<OutboxClaim> claimBatch(String ownerToken, int batchSize, Duration leaseTime);

    /**
     * 仅由当前所有者将事件标记为已发布。
     *
     * @param claim 发布租约
     */
    void markPublished(OutboxClaim claim);

    /**
     * 发布失败时仅由当前所有者释放事件并设置下次可用时间。
     *
     * @param claim 发布租约
     * @param retryDelay 再次发布前的等待时间
     */
    void release(OutboxClaim claim, Duration retryDelay);

    /**
     * 删除指定时间之前已经成功发布的历史事件。
     *
     * @param cutoff 发布时间截止点
     * @return 删除数量
     */
    int deletePublishedBefore(Instant cutoff);
}
