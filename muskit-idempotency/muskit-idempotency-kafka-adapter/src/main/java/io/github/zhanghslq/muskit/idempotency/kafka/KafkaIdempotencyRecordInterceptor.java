package io.github.zhanghslq.muskit.idempotency.kafka;

import io.github.zhanghslq.muskit.idempotency.exception.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.model.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.spi.IdempotencyStore;
import java.time.Duration;
import java.util.Objects;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * 使用 Kafka topic、partition 和 offset 驱动幂等状态机的记录拦截器。
 *
 * @param <K> Kafka 消息键类型
 * @param <V> Kafka 消息值类型
 * @author zhs
 * @since 2026-08-20
 */
public final class KafkaIdempotencyRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    private final IdempotencyStore store;
    private final String operation;
    private final Duration processingTimeout;
    private final Duration retention;
    private final ThreadLocal<IdempotencyClaim> currentClaim = new ThreadLocal<>();

    /**
     * 使用固定操作名称创建 Kafka 幂等记录拦截器。
     *
     * @param store 幂等状态存储
     * @param processingTimeout 消息处理超时时间
     * @param retention 成功消费状态保留时间
     */
    public KafkaIdempotencyRecordInterceptor(
            IdempotencyStore store,
            Duration processingTimeout,
            Duration retention) {
        this(store, "kafka-consume", processingTimeout, retention);
    }

    /**
     * 使用指定操作名称创建 Kafka 幂等记录拦截器。
     *
     * @param store 幂等状态存储
     * @param operation 低基数操作名称
     * @param processingTimeout 消息处理超时时间
     * @param retention 成功消费状态保留时间
     */
    public KafkaIdempotencyRecordInterceptor(
            IdempotencyStore store,
            String operation,
            Duration processingTimeout,
            Duration retention) {
        this.store = Objects.requireNonNull(store, "幂等状态存储不能为空");
        this.operation = Objects.requireNonNull(operation, "幂等操作名称不能为空");
        this.processingTimeout = Objects.requireNonNull(processingTimeout, "消息处理超时时间不能为空");
        this.retention = Objects.requireNonNull(retention, "成功消费状态保留时间不能为空");
    }

    /**
     * 消费前尝试获取消息幂等所有权，已完成消息返回空以跳过监听器。
     *
     * @param record Kafka 消息记录
     * @param consumer Kafka Consumer
     * @return 需要继续处理的记录，已完成时返回空
     */
    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        Objects.requireNonNull(record, "Kafka 消息记录不能为空");
        if (currentClaim.get() != null) {
            throw new IllegalStateException("当前 Kafka 消费线程存在未清理的幂等所有权");
        }
        IdempotencyRequest request = new IdempotencyRequest(
                operation, recordKey(record), processingTimeout, retention);
        IdempotencyAttempt attempt = store.tryStart(request);
        if (attempt.decision() == IdempotencyDecision.COMPLETED) {
            return null;
        }
        if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
            throw new IdempotencyInProgressException(operation);
        }
        currentClaim.set(attempt.claim().orElseThrow(
                () -> new IllegalStateException("幂等存储未返回 Kafka 消息所有权")));
        return record;
    }

    /**
     * 监听器成功处理消息后提交幂等成功状态。
     *
     * @param record Kafka 消息记录
     * @param consumer Kafka Consumer
     */
    @Override
    public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        IdempotencyClaim claim = removeClaim();
        if (claim != null) {
            store.complete(claim);
        }
    }

    /**
     * 监听器处理失败后释放幂等状态，使 Kafka 重投可以重试。
     *
     * @param record Kafka 消息记录
     * @param exception 监听器异常
     * @param consumer Kafka Consumer
     */
    @Override
    public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
        IdempotencyClaim claim = removeClaim();
        if (claim == null) {
            return;
        }
        try {
            store.release(claim);
        } catch (RuntimeException stateFailure) {
            if (exception == null) {
                throw stateFailure;
            }
            exception.addSuppressed(stateFailure);
        }
    }

    /**
     * 在容器清理消费线程状态时移除残留 ThreadLocal，避免线程复用泄漏。
     *
     * @param consumer Kafka Consumer
     */
    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        currentClaim.remove();
    }

    /**
     * 组合 Kafka 记录的稳定唯一业务键。
     *
     * @param record Kafka 消息记录
     * @return topic、partition 和 offset 组合键
     */
    private String recordKey(ConsumerRecord<K, V> record) {
        return record.topic() + ':' + record.partition() + ':' + record.offset();
    }

    /**
     * 返回并移除当前线程持有的消息幂等所有权。
     *
     * @return 当前所有权，不存在时返回空
     */
    private IdempotencyClaim removeClaim() {
        IdempotencyClaim claim = currentClaim.get();
        currentClaim.remove();
        return claim;
    }
}
