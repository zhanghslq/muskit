package io.github.zhanghslq.muskit.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 已分配内部标识和创建时间的不可变 Outbox 事件。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxEvent {

    private final UUID id;
    private final String destination;
    private final String key;
    private final byte[] payload;
    private final Map<String, String> headers;
    private final Instant createdAt;

    /**
     * 创建 Outbox 事件。
     *
     * @param id 内部事件标识
     * @param destination 消息目的地
     * @param key 消息分区键
     * @param payload 消息负载
     * @param headers 消息头
     * @param createdAt 创建时间
     */
    public OutboxEvent(
            UUID id,
            String destination,
            String key,
            byte[] payload,
            Map<String, String> headers,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Outbox 事件标识不能为空");
        OutboxMessageRequest request = new OutboxMessageRequest(destination, key, payload, headers);
        this.destination = request.destination();
        this.key = request.key();
        this.payload = request.payload();
        this.headers = request.headers();
        this.createdAt = Objects.requireNonNull(createdAt, "Outbox 创建时间不能为空");
    }

    /**
     * 返回内部事件标识。
     *
     * @return 事件标识
     */
    public UUID id() {
        return id;
    }

    /**
     * 返回消息目的地。
     *
     * @return 消息目的地
     */
    public String destination() {
        return destination;
    }

    /**
     * 返回消息分区键。
     *
     * @return 消息分区键
     */
    public String key() {
        return key;
    }

    /**
     * 返回消息负载的防御性副本。
     *
     * @return 消息负载副本
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * 返回不可变消息头。
     *
     * @return 消息头
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 返回事件创建时间。
     *
     * @return 创建时间
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 返回不包含事件标识、分区键和消息内容的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "OutboxEvent[destination=" + destination
                + ", payloadBytes=" + payload.length
                + ", headerCount=" + headers.size() + ']';
    }
}
