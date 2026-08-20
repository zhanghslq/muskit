package io.github.zhanghslq.muskit.idempotency.rabbit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

/**
 * 可加入 RabbitListener 容器 Advice Chain 的消息幂等拦截器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class RabbitIdempotencyAdvice implements MethodInterceptor {

    private final IdempotencyStore store;
    private final String operation;
    private final Duration processingTimeout;
    private final Duration retention;
    private final RabbitMessageKeyResolver keyResolver;

    /**
     * 使用 RabbitMQ messageId 作为幂等键创建拦截器。
     *
     * @param store 幂等状态存储
     * @param operation 低基数消费操作名称
     * @param processingTimeout 消息处理超时时间
     * @param retention 成功消费状态保留时间
     */
    public RabbitIdempotencyAdvice(
            IdempotencyStore store,
            String operation,
            Duration processingTimeout,
            Duration retention) {
        this(store, operation, processingTimeout, retention, message -> message.getMessageProperties().getMessageId());
    }

    /**
     * 使用自定义消息键解析策略创建拦截器。
     *
     * @param store 幂等状态存储
     * @param operation 低基数消费操作名称
     * @param processingTimeout 消息处理超时时间
     * @param retention 成功消费状态保留时间
     * @param keyResolver 消息幂等键解析策略
     */
    public RabbitIdempotencyAdvice(
            IdempotencyStore store,
            String operation,
            Duration processingTimeout,
            Duration retention,
            RabbitMessageKeyResolver keyResolver) {
        this.store = Objects.requireNonNull(store, "幂等状态存储不能为空");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ 幂等操作名称不能为空");
        }
        this.operation = operation;
        this.processingTimeout = Objects.requireNonNull(processingTimeout, "消息处理超时时间不能为空");
        this.retention = Objects.requireNonNull(retention, "成功状态保留时间不能为空");
        this.keyResolver = Objects.requireNonNull(keyResolver, "RabbitMQ 消息键解析策略不能为空");
    }

    /**
     * 获取消息所有权，成功时调用监听器，失败时释放，已完成时直接跳过监听器。
     *
     * @param invocation RabbitListener 容器监听器调用
     * @return 原监听器结果，已完成重复消息返回空
     * @throws Throwable 监听器或状态机异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Message message = resolveMessage(invocation.getArguments());
        String key = keyResolver.resolve(message);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("RabbitMQ 幂等消息键不能为空，请设置 messageId 或自定义键解析策略");
        }
        IdempotencyRequest request = new IdempotencyRequest(
                operation, key, processingTimeout, retention);
        IdempotencyAttempt attempt = store.tryStart(request);
        if (attempt.decision() == IdempotencyDecision.COMPLETED) {
            return null;
        }
        if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
            throw new IdempotencyInProgressException(operation);
        }
        IdempotencyClaim claim = attempt.claim().orElseThrow(
                () -> new IllegalStateException("幂等存储未返回 RabbitMQ 消息所有权"));

        try {
            Object result = invocation.proceed();
            store.complete(claim);
            return result;
        } catch (Throwable businessFailure) {
            // 业务异常保持为主异常，状态释放异常作为 suppressed 信息保留。
            try {
                store.release(claim);
            } catch (RuntimeException stateFailure) {
                businessFailure.addSuppressed(stateFailure);
            }
            throw businessFailure;
        }
    }

    /**
     * 从 RabbitListener Advice 参数中定位单条原始消息。
     *
     * @param arguments 容器 Advice 调用参数
     * @return 单条 RabbitMQ 消息
     */
    private Message resolveMessage(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                return message;
            }
            if (argument instanceof List<?> messages && messages.stream().anyMatch(Message.class::isInstance)) {
                if (messages.size() != 1) {
                    throw new IllegalArgumentException("RabbitMQ 幂等 Advice 暂不支持批量监听器");
                }
                return (Message) messages.getFirst();
            }
        }
        throw new IllegalStateException("RabbitListener Advice 调用中未找到原始 Message");
    }
}
