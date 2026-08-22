package io.github.zhanghslq.muskit.outbox.model;

import java.util.Objects;

/**
 * 后台发布实例原子取得的 Outbox 事件租约。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxClaim {

    private final OutboxEvent event;
    private final String ownerToken;
    private final int attempt;

    /**
     * 创建 Outbox 发布租约。
     *
     * @param event 待发布事件
     * @param ownerToken 发布实例所有权令牌
     * @param attempt 当前发布尝试次数
     */
    public OutboxClaim(OutboxEvent event, String ownerToken, int attempt) {
        this.event = Objects.requireNonNull(event, "Outbox 事件不能为空");
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Outbox 所有权令牌不能为空");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("Outbox 发布尝试次数必须大于 0");
        }
        this.ownerToken = ownerToken;
        this.attempt = attempt;
    }

    /**
     * 返回待发布事件。
     *
     * @return Outbox 事件
     */
    public OutboxEvent event() {
        return event;
    }

    /**
     * 返回内部所有权令牌。
     *
     * @return 所有权令牌
     */
    public String ownerToken() {
        return ownerToken;
    }

    /**
     * 返回当前发布尝试次数。
     *
     * @return 尝试次数
     */
    public int attempt() {
        return attempt;
    }

    /**
     * 返回不包含事件标识、所有权令牌或消息内容的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "OutboxClaim[destination=" + event.destination() + ", attempt=" + attempt + ']';
    }
}
