package io.github.zhanghslq.muskit.inbox.model;

/**
 * Inbox 存储原子授予的消息处理租约。
 *
 * @param consumer 低基数消费者名称
 * @param messageId 业务消息唯一标识
 * @param ownerToken 内部所有权令牌
 * @param attempt 当前处理次数
 * @param retention 成功状态保留时间
 * @author zhs
 * @since 2026-08-20
 */
public record InboxClaim(
        String consumer,
        String messageId,
        String ownerToken,
        int attempt,
        java.time.Duration retention) {

    /**
     * 校验并创建 Inbox 处理租约。
     */
    public InboxClaim {
        if (consumer == null || consumer.isBlank() || messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Inbox 租约消费者和消息 ID 不能为空");
        }
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Inbox 所有权令牌不能为空");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("Inbox 当前处理次数必须大于 0");
        }
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("Inbox 成功保留时间必须大于 0");
        }
    }

    /**
     * 返回不暴露消息 ID 和所有权令牌的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "InboxClaim[consumer=" + consumer + ", attempt=" + attempt + ']';
    }
}
