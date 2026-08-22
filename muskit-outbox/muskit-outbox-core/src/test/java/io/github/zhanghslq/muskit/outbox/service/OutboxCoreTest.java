package io.github.zhanghslq.muskit.outbox.service;

import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import io.github.zhanghslq.muskit.outbox.exception.OutboxInterruptedException;
import io.github.zhanghslq.muskit.outbox.model.OutboxClaim;
import io.github.zhanghslq.muskit.outbox.model.OutboxDispatchReport;
import io.github.zhanghslq.muskit.outbox.model.OutboxEvent;
import io.github.zhanghslq.muskit.outbox.model.OutboxMessageRequest;
import io.github.zhanghslq.muskit.outbox.model.OutboxRetryPolicy;
import io.github.zhanghslq.muskit.outbox.service.OutboxDispatchService;
import io.github.zhanghslq.muskit.outbox.service.OutboxService;
import io.github.zhanghslq.muskit.outbox.spi.OutboxPublisher;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 核心发布与调度语义测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class OutboxCoreTest {

    /**
     * 验证业务入口创建不可变事件且安全描述不暴露消息内容。
     */
    @Test
    void shouldAppendImmutableEventWithoutSensitiveToString() {
        InMemoryRepository repository = new InMemoryRepository();
        OutboxService service = new OutboxService(
                repository, Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        byte[] payload = {1, 2, 3};
        OutboxMessageRequest request = new OutboxMessageRequest(
                "order-created", "sensitive-key", payload, Map.of("trace", "secret-header"));

        service.publish(request);
        payload[0] = 9;

        OutboxEvent event = repository.events.getFirst();
        assertThat(event.payload()).containsExactly(1, 2, 3);
        assertThat(event.toString())
                .doesNotContain("sensitive-key")
                .doesNotContain("secret-header")
                .doesNotContain(event.id().toString());
    }

    /**
     * 验证成功事件被确认，普通失败事件被释放等待重试。
     */
    @Test
    void shouldPublishSuccessfulEventsAndReleaseFailures() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.events.add(event("success"));
        repository.events.add(event("failure"));
        OutboxPublisher publisher = event -> {
            if (event.destination().equals("failure")) {
                throw new IllegalStateException("broker unavailable");
            }
        };
        OutboxDispatchService dispatcher = new OutboxDispatchService(
                repository, publisher, 10, Duration.ofSeconds(30), Duration.ofSeconds(5));

        OutboxDispatchReport report = dispatcher.dispatchBatch();

        assertThat(report).isEqualTo(new OutboxDispatchReport(2, 1, 1));
        assertThat(repository.published).hasSize(1);
        assertThat(repository.released).hasSize(1);
    }

    /**
     * 验证发布被中断时恢复中断标记并释放剩余租约。
     */
    @Test
    void shouldRestoreInterruptFlagAndReleaseRemainingClaims() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.events.add(event("first"));
        repository.events.add(event("second"));
        OutboxDispatchService dispatcher = new OutboxDispatchService(
                repository,
                event -> {
                    throw new InterruptedException("shutdown");
                },
                10,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5));

        try {
            assertThatThrownBy(dispatcher::dispatchBatch).isInstanceOf(OutboxInterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(repository.released).hasSize(2);
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * 验证达到最大尝试次数后进入死信并调用外部死信通知。
     */
    @Test
    void shouldMarkDeadAfterMaxAttempts() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.events.add(event("failure"));
        List<OutboxClaim> notified = new ArrayList<>();
        OutboxDispatchService dispatcher = new OutboxDispatchService(
                repository,
                event -> { throw new IllegalStateException("broker unavailable"); },
                10,
                Duration.ofSeconds(30),
                new OutboxRetryPolicy(1, Duration.ofSeconds(1), 2D, Duration.ofSeconds(10)),
                io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry.noop(),
                notified::add);

        OutboxDispatchReport report = dispatcher.dispatchBatch();

        assertThat(report).isEqualTo(new OutboxDispatchReport(1, 0, 1, 1));
        assertThat(repository.dead).hasSize(1);
        assertThat(notified).hasSize(1);
    }

    /**
     * 创建测试 Outbox 事件。
     *
     * @param destination 消息目的地
     * @return Outbox 事件
     */
    private OutboxEvent event(String destination) {
        return new OutboxEvent(
                java.util.UUID.randomUUID(),
                destination,
                "key",
                new byte[] {1},
                Map.of(),
                Instant.parse("2026-08-20T00:00:00Z"));
    }

    /**
     * 提供可观测状态的内存 Outbox 存储测试替身。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class InMemoryRepository implements OutboxRepository {

        private final List<OutboxEvent> events = new ArrayList<>();
        private final List<OutboxClaim> published = new ArrayList<>();
        private final List<OutboxClaim> released = new ArrayList<>();
        private final List<OutboxClaim> dead = new ArrayList<>();

        /**
         * 创建内存测试存储。
         */
        private InMemoryRepository() {
        }

        /**
         * 保存测试事件。
         *
         * @param event Outbox 事件
         */
        @Override
        public void append(OutboxEvent event) {
            events.add(event);
        }

        /**
         * 返回当前全部事件的测试租约。
         *
         * @param ownerToken 发布实例令牌
         * @param batchSize 最大批量大小
         * @param leaseTime 发布租约时间
         * @return 测试租约
         */
        @Override
        public List<OutboxClaim> claimBatch(String ownerToken, int batchSize, Duration leaseTime) {
            return events.stream()
                    .limit(batchSize)
                    .map(event -> new OutboxClaim(event, ownerToken, 1))
                    .toList();
        }

        /**
         * 记录成功发布租约。
         *
         * @param claim 发布租约
         */
        @Override
        public void markPublished(OutboxClaim claim) {
            published.add(claim);
        }

        /**
         * 记录释放的测试租约。
         *
         * @param claim 发布租约
         * @param retryDelay 再次发布前等待时间
         */
        @Override
        public void release(OutboxClaim claim, Duration retryDelay) {
            released.add(claim);
        }

        /**
         * 记录进入死信的测试租约。
         *
         * @param claim 发布租约
         * @param reasonCode 失败原因编码
         */
        @Override
        public void markDead(OutboxClaim claim, String reasonCode) {
            dead.add(claim);
        }

        /**
         * 内存测试存储不保留已发布历史。
         *
         * @param cutoff 发布时间截止点
         * @return 删除数量
         */
        @Override
        public int deletePublishedBefore(Instant cutoff) {
            return 0;
        }
    }
}
