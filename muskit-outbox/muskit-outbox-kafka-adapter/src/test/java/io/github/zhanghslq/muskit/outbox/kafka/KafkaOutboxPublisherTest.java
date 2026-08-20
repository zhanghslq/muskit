package io.github.zhanghslq.muskit.outbox.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.github.zhanghslq.muskit.outbox.OutboxEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka Outbox 发布器测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class KafkaOutboxPublisherTest {

    /**
     * 验证事件字段和消息头会完整映射到 Kafka 消息。
     *
     * @throws Exception 发布失败
    */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldPublishRecordAndWaitForAcknowledgement() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, byte[]> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(kafkaTemplate);

        publisher.publish(event());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, byte[]> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("orders");
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(record.value()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        assertThat(record.headers().lastHeader("type").value())
                .isEqualTo("created".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 验证 broker 异常会以原始异常类型传播。
    */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldPropagateBrokerFailure() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
        IllegalStateException failure = new IllegalStateException("broker unavailable");
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));
        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(kafkaTemplate);

        assertThatThrownBy(() -> publisher.publish(event())).isSameAs(failure);
    }

    /**
     * 创建测试事件。
     *
     * @return 测试事件
     */
    private OutboxEvent event() {
        return new OutboxEvent(
                UUID.randomUUID(),
                "orders",
                "order-1",
                "payload".getBytes(StandardCharsets.UTF_8),
                Map.of("type", "created"),
                Instant.parse("2026-08-20T00:00:00Z"));
    }
}
