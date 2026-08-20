package io.github.zhanghslq.muskit.outbox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 请求在当前数据库事务中写入的不可变 Outbox 消息。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxMessageRequest {

    private final String destination;
    private final String key;
    private final byte[] payload;
    private final Map<String, String> headers;

    /**
     * 创建 Outbox 消息请求。
     *
     * @param destination 消息目的地，例如 Kafka topic
     * @param key 消息分区键，可为空
     * @param payload 消息负载
     * @param headers 消息头
     */
    public OutboxMessageRequest(
            String destination,
            String key,
            byte[] payload,
            Map<String, String> headers) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Outbox 消息目的地不能为空");
        }
        this.destination = destination;
        this.key = key == null ? "" : key;
        this.payload = Objects.requireNonNull(payload, "Outbox 消息负载不能为空").clone();
        Objects.requireNonNull(headers, "Outbox 消息头不能为空");
        Map<String, String> copiedHeaders = new LinkedHashMap<>();
        headers.forEach((name, value) -> copiedHeaders.put(
                Objects.requireNonNull(name, "Outbox 消息头名称不能为空"),
                Objects.requireNonNull(value, "Outbox 消息头值不能为空")));
        this.headers = Map.copyOf(copiedHeaders);
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
     * @return 消息分区键，可为空字符串
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
     * 返回不包含分区键、负载或消息头值的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "OutboxMessageRequest[destination=" + destination
                + ", payloadBytes=" + payload.length
                + ", headerCount=" + headers.size() + ']';
    }
}
