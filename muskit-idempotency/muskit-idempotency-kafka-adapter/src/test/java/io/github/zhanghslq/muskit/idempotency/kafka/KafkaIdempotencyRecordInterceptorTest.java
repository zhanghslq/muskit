package io.github.zhanghslq.muskit.idempotency.kafka;

import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka 幂等记录拦截器单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class KafkaIdempotencyRecordInterceptorTest {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration RETENTION = Duration.ofDays(1);

    /**
     * 验证成功消费会使用消息位置获取并完成幂等所有权。
     */
    @Test
    void shouldAcquireAndCompleteRecord() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyClaim claim = claim();
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.acquired(claim));
        KafkaIdempotencyRecordInterceptor<String, String> interceptor = interceptor(store);
        ConsumerRecord<String, String> record = record();

        assertThat(interceptor.intercept(record, consumer())).isSameAs(record);
        interceptor.success(record, consumer());

        ArgumentCaptor<IdempotencyRequest> requestCaptor = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(store).tryStart(requestCaptor.capture());
        assertThat(requestCaptor.getValue().operation()).isEqualTo("order-consume");
        assertThat(requestCaptor.getValue().key()).isEqualTo("orders:2:42");
        verify(store).complete(claim);
    }

    /**
     * 验证已成功处理的消息会被跳过而不进入监听器。
     */
    @Test
    void shouldSkipCompletedRecord() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.COMPLETED));

        assertThat(interceptor(store).intercept(record(), consumer())).isNull();
    }

    /**
     * 验证仍在其他消费者处理中时抛出可重试异常。
     */
    @Test
    void shouldRejectInProgressRecord() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.IN_PROGRESS));

        assertThatThrownBy(() -> interceptor(store).intercept(record(), consumer()))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    /**
     * 验证监听器失败会释放所有权，并保留状态存储失败供诊断。
     */
    @Test
    void shouldReleaseClaimAndSuppressStoreFailure() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyClaim claim = claim();
        RuntimeException stateFailure = new IllegalStateException("state unavailable");
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.acquired(claim));
        org.mockito.Mockito.doThrow(stateFailure).when(store).release(claim);
        KafkaIdempotencyRecordInterceptor<String, String> interceptor = interceptor(store);
        interceptor.intercept(record(), consumer());
        Exception listenerFailure = new IllegalArgumentException("listener failed");

        interceptor.failure(record(), listenerFailure, consumer());

        verify(store).release(claim);
        assertThat(listenerFailure.getSuppressed()).containsExactly(stateFailure);
    }

    /**
     * 创建待测试拦截器。
     *
     * @param store 幂等状态存储
     * @return Kafka 幂等记录拦截器
     */
    private KafkaIdempotencyRecordInterceptor<String, String> interceptor(IdempotencyStore store) {
        return new KafkaIdempotencyRecordInterceptor<>(
                store, "order-consume", PROCESSING_TIMEOUT, RETENTION);
    }

    /**
     * 创建测试 Kafka 消息。
     *
     * @return 测试消息
     */
    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("orders", 2, 42L, "order-key", "payload");
    }

    /**
     * 创建测试幂等所有权。
     *
     * @return 幂等所有权
     */
    private IdempotencyClaim claim() {
        return new IdempotencyClaim("order-consume", "orders:2:42", "owner-token", RETENTION);
    }

    /**
     * 创建不参与本测试行为的 Kafka Consumer。
     *
     * @return Kafka Consumer mock
     */
    @SuppressWarnings("unchecked")
    private Consumer<String, String> consumer() {
        return mock(Consumer.class);
    }
}
