package io.github.zhanghslq.muskit.inbox.model;

/**
 * 使用业务消息 ID 而非 Broker 位点描述的一次 Inbox 请求。
 *
 * @param consumer 低基数消费者名称
 * @param messageId 业务消息唯一标识
 * @param processingTimeout 处理租约时间
 * @param retention 成功状态保留时间
 * @author zhs
 * @since 2026-08-20
 */
public record InboxRequest(
        String consumer,
        String messageId,
        java.time.Duration processingTimeout,
        java.time.Duration retention) {

    /**
     * 校验并创建 Inbox 请求。
     */
    public InboxRequest {
        if (consumer == null || consumer.isBlank() || consumer.length() > 128) {
            throw new IllegalArgumentException("Inbox 消费者名称不能为空且长度不能超过 128");
        }
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Inbox 业务消息 ID 不能为空");
        }
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("Inbox 处理租约必须大于 0");
        }
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("Inbox 成功保留时间必须大于 0");
        }
    }

    /**
     * 返回不暴露业务消息 ID 的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "InboxRequest[consumer=" + consumer + ']';
    }
}
