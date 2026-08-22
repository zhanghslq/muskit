package io.github.zhanghslq.muskit.inbox.service;

import io.github.zhanghslq.muskit.inbox.model.InboxAttempt;
import io.github.zhanghslq.muskit.inbox.model.InboxClaim;
import io.github.zhanghslq.muskit.inbox.model.InboxDecision;
import io.github.zhanghslq.muskit.inbox.model.InboxPolicy;
import io.github.zhanghslq.muskit.inbox.model.InboxProcessResult;
import io.github.zhanghslq.muskit.inbox.model.InboxRequest;
import io.github.zhanghslq.muskit.inbox.model.InboxStatus;
import io.github.zhanghslq.muskit.inbox.service.InboxProcessor;
import io.github.zhanghslq.muskit.inbox.spi.InboxStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inbox 处理、重试、去重和死信测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class InboxProcessorTest {

    private static final InboxPolicy POLICY = new InboxPolicy(
            "orders", Duration.ofSeconds(30), Duration.ofDays(1), 2,
            Duration.ofSeconds(1), 2D, Duration.ofSeconds(10));

    /**
     * 验证成功消息只执行业务一次，后续重复消息直接跳过。
     *
     * @throws Exception 业务处理异常
     */
    @Test
    void shouldProcessOnlyOnceAfterSuccess() throws Exception {
        InMemoryInboxStore store = new InMemoryInboxStore();
        InboxProcessor processor = new InboxProcessor(store);
        AtomicInteger calls = new AtomicInteger();

        assertThat(processor.process("order-consumer", "message-1", POLICY, calls::incrementAndGet))
                .isEqualTo(InboxProcessResult.PROCESSED);
        assertThat(processor.process("order-consumer", "message-1", POLICY, calls::incrementAndGet))
                .isEqualTo(InboxProcessResult.DUPLICATE);
        assertThat(calls).hasValue(1);
    }

    /**
     * 验证失败消息先进入重试，再达到最大次数进入死信并支持人工回放。
     *
     * @throws Exception 死信跳过调用声明的业务异常
     */
    @Test
    void shouldRetryThenDeadAndReplay() throws Exception {
        InMemoryInboxStore store = new InMemoryInboxStore();
        InboxProcessor processor = new InboxProcessor(store);

        assertThatThrownBy(() -> processor.process(
                "order-consumer", "message-2", POLICY,
                () -> { throw new IllegalStateException("failure"); }))
                .isInstanceOf(IllegalStateException.class);
        store.makeRetryAvailable("message-2");
        assertThatThrownBy(() -> processor.process(
                "order-consumer", "message-2", POLICY,
                () -> { throw new IllegalStateException("failure"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processor.process("order-consumer", "message-2", POLICY, () -> { }))
                .isEqualTo(InboxProcessResult.DEAD);
        assertThat(processor.replayDead("order-consumer", "message-2")).isTrue();
    }

    /**
     * 提供确定性状态转换的内存 Inbox 存储测试替身。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class InMemoryInboxStore implements InboxStore {

        private final Map<String, InboxStatus> statuses = new HashMap<>();
        private final Map<String, Integer> attempts = new HashMap<>();

        /**
         * 创建内存 Inbox 存储。
         */
        private InMemoryInboxStore() {
        }

        /**
         * 按内存状态竞争处理租约。
         *
         * @param request Inbox 请求
         * @return 竞争结果
         */
        @Override
        public InboxAttempt tryClaim(InboxRequest request) {
            InboxStatus status = statuses.get(request.messageId());
            if (status == InboxStatus.SUCCEEDED) {
                return InboxAttempt.rejected(InboxDecision.SUCCEEDED, Duration.ZERO);
            }
            if (status == InboxStatus.DEAD) {
                return InboxAttempt.rejected(InboxDecision.DEAD, Duration.ZERO);
            }
            if (status == InboxStatus.RETRY_WAIT) {
                return InboxAttempt.rejected(InboxDecision.RETRY_LATER, Duration.ofSeconds(1));
            }
            int attempt = attempts.merge(request.messageId(), 1, Integer::sum);
            statuses.put(request.messageId(), InboxStatus.PROCESSING);
            return InboxAttempt.acquired(new InboxClaim(
                    request.consumer(), request.messageId(), "owner", attempt, request.retention()));
        }

        /**
         * 提交成功状态。
         *
         * @param claim 处理租约
         */
        @Override
        public void complete(InboxClaim claim) {
            statuses.put(claim.messageId(), InboxStatus.SUCCEEDED);
        }

        /**
         * 标记等待重试。
         *
         * @param claim 处理租约
         * @param retryDelay 重试等待时间
         * @param reasonCode 原因编码
         */
        @Override
        public void retry(InboxClaim claim, Duration retryDelay, String reasonCode) {
            statuses.put(claim.messageId(), InboxStatus.RETRY_WAIT);
        }

        /**
         * 标记死信。
         *
         * @param claim 处理租约
         * @param reasonCode 原因编码
         */
        @Override
        public void markDead(InboxClaim claim, String reasonCode) {
            statuses.put(claim.messageId(), InboxStatus.DEAD);
        }

        /**
         * 人工恢复死信。
         *
         * @param consumer 消费者名称
         * @param messageId 消息 ID
         * @return 是否恢复
         */
        @Override
        public boolean replayDead(String consumer, String messageId) {
            if (statuses.get(messageId) != InboxStatus.DEAD) {
                return false;
            }
            statuses.put(messageId, InboxStatus.RETRY_WAIT);
            return true;
        }

        /**
         * 让测试重试消息立即可获取。
         *
         * @param messageId 消息 ID
         */
        private void makeRetryAvailable(String messageId) {
            statuses.remove(messageId);
        }
    }
}
