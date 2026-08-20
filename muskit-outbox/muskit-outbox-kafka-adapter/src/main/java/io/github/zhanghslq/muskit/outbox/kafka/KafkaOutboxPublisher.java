package io.github.zhanghslq.muskit.outbox.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import io.github.zhanghslq.muskit.outbox.OutboxEvent;
import io.github.zhanghslq.muskit.outbox.OutboxPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 等待 Kafka broker 确认后返回的 Outbox 发布器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    /**
     * 创建 Kafka Outbox 发布器。
     *
     * @param kafkaTemplate Kafka 发送模板
     */
    public KafkaOutboxPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate 不能为空");
    }

    /**
     * 将 Outbox 事件映射为 Kafka 消息并等待 broker 确认。
     *
     * @param event Outbox 事件
     * @throws Exception Kafka 发布失败或等待被中断
     */
    @Override
    public void publish(OutboxEvent event) throws Exception {
        Objects.requireNonNull(event, "Outbox 事件不能为空");
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                event.destination(),
                event.key().isBlank() ? null : event.key(),
                event.payload());
        for (Map.Entry<String, String> header : event.headers().entrySet()) {
            record.headers().add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
        }
        try {
            // 必须等待 broker 确认，调用方才能安全地把数据库事件标记为已发布。
            kafkaTemplate.send(record).get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Kafka Outbox 发布失败", cause);
        }
    }
}
