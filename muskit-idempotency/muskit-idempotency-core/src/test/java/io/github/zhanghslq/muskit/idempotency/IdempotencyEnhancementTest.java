package io.github.zhanghslq.muskit.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务 ID 模板、批量处理和 lease 续期测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
class IdempotencyEnhancementTest {

    /**
     * 验证批量模板分类跳过重复和处理中业务 ID。
     */
    @Test
    void shouldProcessBusinessIdBatch() {
        InMemoryStore store = new InMemoryStore();
        store.states.put("done", IdempotencyDecision.COMPLETED);
        store.states.put("busy", IdempotencyDecision.IN_PROGRESS);
        AtomicInteger calls = new AtomicInteger();

        IdempotencyBatchReport report = new IdempotencyTemplate(store).executeBatch(
                "order.import",
                List.of("new", "done", "busy"),
                Duration.ofSeconds(10),
                Duration.ofHours(1),
                ignored -> calls.incrementAndGet());

        assertThat(report.completed()).isEqualTo(1);
        assertThat(report.duplicate()).isEqualTo(1);
        assertThat(report.inProgress()).isEqualTo(1);
        assertThat(calls).hasValue(1);
    }

    /**
     * 验证 lease 在业务持有期间定时调用 Provider 续期。
     *
     * @throws Exception 等待定时任务异常
     */
    @Test
    void shouldRenewLease() throws Exception {
        InMemoryStore store = new InMemoryStore();
        IdempotencyClaim claim = new IdempotencyClaim(
                "order.import", "business-1", "owner-1", Duration.ofHours(1));
        try (var scheduler = Executors.newSingleThreadScheduledExecutor();
                IdempotencyLease ignored = IdempotencyLease.start(
                        store, claim, Duration.ofMillis(30), scheduler)) {
            assertThat(store.renewed.await(1, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(store.renewals.get()).isPositive();
    }

    /**
     * 测试用内存幂等存储。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class InMemoryStore implements IdempotencyStore {

        private final Map<String, IdempotencyDecision> states = new ConcurrentHashMap<>();
        private final AtomicInteger renewals = new AtomicInteger();
        private final CountDownLatch renewed = new CountDownLatch(1);

        /**
         * 尝试创建测试处理中状态。
         *
         * @param request 幂等请求
         * @return 尝试结果
         */
        @Override
        public IdempotencyAttempt tryStart(IdempotencyRequest request) {
            IdempotencyDecision existing = states.putIfAbsent(request.key(), IdempotencyDecision.IN_PROGRESS);
            if (existing != null) {
                return IdempotencyAttempt.rejected(existing);
            }
            return IdempotencyAttempt.acquired(new IdempotencyClaim(
                    request.operation(), request.key(), "owner", request.retention()));
        }

        /**
         * 提交测试完成状态。
         *
         * @param claim 所有权声明
         */
        @Override
        public void complete(IdempotencyClaim claim) {
            states.put(claim.key(), IdempotencyDecision.COMPLETED);
        }

        /**
         * 统计一次测试续期。
         *
         * @param claim 所有权声明
         * @param processingTimeout 新处理超时时间
         */
        @Override
        public void renew(IdempotencyClaim claim, Duration processingTimeout) {
            renewals.incrementAndGet();
            renewed.countDown();
        }

        /**
         * 释放测试处理中状态。
         *
         * @param claim 所有权声明
         */
        @Override
        public void release(IdempotencyClaim claim) {
            states.remove(claim.key());
        }

        /**
         * 测试存储不提供结果重放。
         *
         * @param request 幂等请求
         * @return 空结果
         */
        @Override
        public Optional<IdempotencyResult> findCompletedResult(IdempotencyRequest request) {
            return Optional.empty();
        }
    }
}
