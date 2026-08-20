package io.github.zhanghslq.muskit.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
     * 返回尚未成功发布的事件数量；不支持统计的实现返回负数。
     *
     * @return 待发布数量，负数表示 Provider 不支持统计
     */
    default long countPending() {
        return -1L;
    }

    /**
     * 仅由当前所有者把事件标记为死信。
     *
     * @param claim 发布租约
     * @param reasonCode 低基数失败原因编码
     */
    default void markDead(OutboxClaim claim, String reasonCode) {
        throw new UnsupportedOperationException("当前 Outbox Provider 不支持死信状态");
    }

    /**
     * 将指定死信事件恢复为立即可发布状态。
     *
     * @param eventId Outbox 事件标识
     * @return 是否成功恢复
     */
    default boolean replayDead(UUID eventId) {
        return false;
    }

    /**
     * 返回死信事件数量；不支持统计的实现返回负数。
     *
     * @return 死信数量
     */
    default long countDead() {
        return -1L;
    }

    /**
     * 删除指定时间之前已经成功发布的历史事件。
     *
     * @param cutoff 发布时间截止点
     * @return 删除数量
     */
    int deletePublishedBefore(Instant cutoff);
}
