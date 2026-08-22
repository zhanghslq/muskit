package io.github.zhanghslq.muskit.inbox.spi;

import io.github.zhanghslq.muskit.inbox.model.InboxAttempt;
import io.github.zhanghslq.muskit.inbox.model.InboxClaim;
import io.github.zhanghslq.muskit.inbox.model.InboxRequest;
import java.time.Duration;

/**
 * Inbox 消息状态、处理租约、死信与人工回放存储 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface InboxStore {

    /**
     * 原子竞争消息处理所有权。
     *
     * @param request Inbox 请求
     * @return 竞争判定
     */
    InboxAttempt tryClaim(InboxRequest request);

    /**
     * 仅由当前所有者提交成功状态。
     *
     * @param claim 处理租约
     */
    void complete(InboxClaim claim);

    /**
     * 仅由当前所有者把失败消息转为等待重试状态。
     *
     * @param claim 处理租约
     * @param retryDelay 重试等待时间
     * @param reasonCode 低基数失败原因编码
     */
    void retry(InboxClaim claim, Duration retryDelay, String reasonCode);

    /**
     * 仅由当前所有者把消息转为死信状态。
     *
     * @param claim 处理租约
     * @param reasonCode 低基数失败原因编码
     */
    void markDead(InboxClaim claim, String reasonCode);

    /**
     * 将死信消息恢复为立即可重试状态。
     *
     * @param consumer 消费者名称
     * @param messageId 业务消息 ID
     * @return 是否成功恢复
     */
    boolean replayDead(String consumer, String messageId);

    /**
     * 返回指定消费者的死信数量；不支持统计的实现返回负数。
     *
     * @param consumer 消费者名称
     * @return 死信数量
     */
    default long countDead(String consumer) {
        return -1L;
    }
}
