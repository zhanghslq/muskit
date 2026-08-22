package io.github.zhanghslq.muskit.test.outbox;

import io.github.zhanghslq.muskit.outbox.exception.OutboxOwnershipLostException;
import io.github.zhanghslq.muskit.outbox.model.OutboxClaim;
import io.github.zhanghslq.muskit.outbox.model.OutboxEvent;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有 Outbox 存储 Provider 都应通过的发布租约契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class OutboxRepositoryContract {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-20T00:00:00Z");

    /**
     * 创建 Outbox 存储契约测试基类。
     */
    protected OutboxRepositoryContract() {
    }

    /**
     * 返回第一个应用实例在指定时刻看到的 Outbox 存储。
     *
     * @param now 当前时刻
     * @return 第一个 Outbox 存储
     */
    protected abstract OutboxRepository firstRepositoryAt(Instant now);

    /**
     * 返回第二个应用实例在指定时刻看到的 Outbox 存储。
     *
     * @param now 当前时刻
     * @return 第二个 Outbox 存储
     */
    protected abstract OutboxRepository secondRepositoryAt(Instant now);

    /**
     * 使用 Provider 要求的事务边界追加事件。
     *
     * @param repository Outbox 存储
     * @param event 待追加事件
     */
    protected abstract void append(OutboxRepository repository, OutboxEvent event);

    /**
     * 验证同一事件只会授予一个实例，并完整保留消息内容。
     */
    @Test
    protected final void shouldClaimOnceAcrossInstancesAndPreserveEvent() {
        OutboxEvent event = event("claim");
        OutboxRepository first = firstRepositoryAt(INITIAL_TIME);
        append(first, event);

        List<OutboxClaim> firstClaims = first.claimBatch("owner-a", 1, Duration.ofSeconds(30));
        List<OutboxClaim> secondClaims = secondRepositoryAt(INITIAL_TIME)
                .claimBatch("owner-b", 1, Duration.ofSeconds(30));

        assertEquals(1, firstClaims.size());
        assertTrue(secondClaims.isEmpty());
        assertArrayEquals(event.payload(), firstClaims.getFirst().event().payload());
        assertEquals(event.headers(), firstClaims.getFirst().event().headers());
        first.markPublished(firstClaims.getFirst());
        if (first.countPending() >= 0) {
            assertEquals(0, first.countPending());
        }
    }

    /**
     * 验证发布失败后必须等待指定时间，重试时增加尝试次数。
     */
    @Test
    protected final void shouldReleaseAndRetryOnlyAfterDelay() {
        OutboxRepository first = firstRepositoryAt(INITIAL_TIME);
        append(first, event("retry"));
        OutboxClaim firstClaim = first.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();

        first.release(firstClaim, Duration.ofSeconds(10));

        assertTrue(secondRepositoryAt(INITIAL_TIME.plusSeconds(9))
                .claimBatch("owner-b", 1, Duration.ofSeconds(30)).isEmpty());
        OutboxClaim retryClaim = secondRepositoryAt(INITIAL_TIME.plusSeconds(11))
                .claimBatch("owner-b", 1, Duration.ofSeconds(30)).getFirst();
        assertEquals(2, retryClaim.attempt());
    }

    /**
     * 验证发布租约过期后其他实例可以接管，旧所有者不能提交成功。
     */
    @Test
    protected final void shouldRejectStaleOwnerAfterLeaseTakeover() {
        OutboxRepository first = firstRepositoryAt(INITIAL_TIME);
        append(first, event("takeover"));
        OutboxClaim staleClaim = first.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();
        OutboxRepository second = secondRepositoryAt(INITIAL_TIME.plusSeconds(31));
        OutboxClaim currentClaim = second.claimBatch("owner-b", 1, Duration.ofSeconds(30)).getFirst();

        assertEquals(2, currentClaim.attempt());
        assertThrows(OutboxOwnershipLostException.class, () -> first.markPublished(staleClaim));
        second.markPublished(currentClaim);
    }

    /**
     * 验证死信事件可以统计和人工回放，回放后重置尝试次数。
     */
    @Test
    protected final void shouldMarkDeadAndReplayFromFirstAttempt() {
        OutboxRepository first = firstRepositoryAt(INITIAL_TIME);
        long deadBefore = first.countDead();
        OutboxEvent event = event("dead");
        append(first, event);
        OutboxClaim claim = first.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();

        first.markDead(claim, "contract-dead");

        if (deadBefore >= 0) {
            assertEquals(deadBefore + 1, first.countDead());
        }
        assertTrue(secondRepositoryAt(INITIAL_TIME).replayDead(event.id()));
        assertEquals(
                1,
                secondRepositoryAt(INITIAL_TIME)
                        .claimBatch("owner-b", 1, Duration.ofSeconds(30))
                        .getFirst()
                        .attempt());
    }

    /**
     * 验证已经发布的历史事件可以按发布时间清理。
     */
    @Test
    protected final void shouldDeletePublishedHistoryBeforeCutoff() {
        OutboxRepository repository = firstRepositoryAt(INITIAL_TIME);
        append(repository, event("cleanup"));
        OutboxClaim claim = repository.claimBatch("owner-a", 1, Duration.ofSeconds(30)).getFirst();
        repository.markPublished(claim);

        assertEquals(1, repository.deletePublishedBefore(INITIAL_TIME.plusSeconds(1)));
    }

    /**
     * 创建带随机标识和分区键的 Outbox 事件。
     *
     * @param scenario 测试场景
     * @return Outbox 事件
     */
    private OutboxEvent event(String scenario) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "provider-contract",
                scenario + '-' + UUID.randomUUID(),
                ("payload-" + scenario).getBytes(StandardCharsets.UTF_8),
                Map.of("type", scenario),
                INITIAL_TIME);
    }
}
