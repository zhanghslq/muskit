package io.github.zhanghslq.muskit.idempotency.rabbit;

import java.time.Duration;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RabbitMQ 消息幂等 Advice 单元测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class RabbitIdempotencyAdviceTest {

    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration RETENTION = Duration.ofDays(1);

    /**
     * 验证成功监听会使用 messageId 获取并完成幂等所有权。
     *
     * @throws Throwable Advice 调用异常
     */
    @Test
    void shouldAcquireAndCompleteMessage() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyClaim claim = claim();
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.acquired(claim));
        MethodInvocation invocation = invocation(message("message-42"));
        when(invocation.proceed()).thenReturn("handled");

        assertThat(advice(store).invoke(invocation)).isEqualTo("handled");

        ArgumentCaptor<IdempotencyRequest> requestCaptor = ArgumentCaptor.forClass(IdempotencyRequest.class);
        verify(store).tryStart(requestCaptor.capture());
        assertThat(requestCaptor.getValue().key()).isEqualTo("message-42");
        verify(store).complete(claim);
    }

    /**
     * 验证已完成重复消息直接跳过监听器。
     *
     * @throws Throwable Advice 调用异常
     */
    @Test
    void shouldSkipCompletedMessage() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.COMPLETED));
        MethodInvocation invocation = invocation(message("completed"));

        assertThat(advice(store).invoke(invocation)).isNull();
        verify(invocation, never()).proceed();
    }

    /**
     * 验证处理中消息抛出明确异常，由容器重试策略决定是否重回队列。
     */
    @Test
    void shouldRejectInProgressMessage() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.rejected(IdempotencyDecision.IN_PROGRESS));

        assertThatThrownBy(() -> advice(store).invoke(invocation(message("processing"))))
                .isInstanceOf(IdempotencyInProgressException.class);
    }

    /**
     * 验证监听器失败会释放状态并保留状态释放异常。
     */
    @Test
    void shouldReleaseClaimWhenListenerFails() throws Throwable {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyClaim claim = claim();
        when(store.tryStart(any())).thenReturn(IdempotencyAttempt.acquired(claim));
        MethodInvocation invocation = invocation(message("failed"));
        IllegalStateException businessFailure = new IllegalStateException("listener failed");
        IllegalArgumentException stateFailure = new IllegalArgumentException("store failed");
        when(invocation.proceed()).thenThrow(businessFailure);
        org.mockito.Mockito.doThrow(stateFailure).when(store).release(claim);

        assertThatThrownBy(() -> advice(store).invoke(invocation))
                .isSameAs(businessFailure);
        assertThat(businessFailure.getSuppressed()).containsExactly(stateFailure);
    }

    /**
     * 验证默认解析策略要求生产者提供 messageId。
     */
    @Test
    void shouldRequireMessageIdByDefault() {
        assertThatThrownBy(() -> advice(mock(IdempotencyStore.class)).invoke(invocation(message(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    /**
     * 创建待测试 RabbitMQ Advice。
     *
     * @param store 幂等状态存储
     * @return RabbitMQ 幂等 Advice
     */
    private RabbitIdempotencyAdvice advice(IdempotencyStore store) {
        return new RabbitIdempotencyAdvice(
                store, "order-consume", PROCESSING_TIMEOUT, RETENTION);
    }

    /**
     * 创建 RabbitMQ 原始消息。
     *
     * @param messageId 消息标识
     * @return RabbitMQ 消息
     */
    private Message message(String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        return new Message(new byte[0], properties);
    }

    /**
     * 创建包含原始消息的 Advice 调用。
     *
     * @param message RabbitMQ 消息
     * @return Advice 调用 mock
     */
    private MethodInvocation invocation(Message message) {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {message});
        return invocation;
    }

    /**
     * 创建测试幂等所有权。
     *
     * @return 幂等所有权
     */
    private IdempotencyClaim claim() {
        return new IdempotencyClaim("order-consume", "message-42", "owner", RETENTION);
    }
}
