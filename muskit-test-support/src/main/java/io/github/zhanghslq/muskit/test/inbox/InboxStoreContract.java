package io.github.zhanghslq.muskit.test.inbox;

import io.github.zhanghslq.muskit.inbox.exception.InboxOwnershipLostException;
import io.github.zhanghslq.muskit.inbox.model.InboxAttempt;
import io.github.zhanghslq.muskit.inbox.model.InboxClaim;
import io.github.zhanghslq.muskit.inbox.model.InboxDecision;
import io.github.zhanghslq.muskit.inbox.model.InboxRequest;
import io.github.zhanghslq.muskit.inbox.spi.InboxStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 所有 Inbox 存储 Provider 都应通过的状态机契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class InboxStoreContract {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-20T00:00:00Z");

    /**
     * 创建 Inbox 存储契约测试基类。
     */
    protected InboxStoreContract() {
    }

    /**
     * 返回第一个应用实例在指定时刻看到的 Inbox 存储。
     *
     * @param now 当前时刻
     * @return 第一个 Inbox 存储
     */
    protected abstract InboxStore firstStoreAt(Instant now);

    /**
     * 返回第二个应用实例在指定时刻看到的 Inbox 存储。
     *
     * @param now 当前时刻
     * @return 第二个 Inbox 存储
     */
    protected abstract InboxStore secondStoreAt(Instant now);

    /**
     * 验证处理租约和成功状态会在多个应用实例之间共享。
     */
    @Test
    protected final void shouldShareProcessingAndSucceededStatesAcrossInstances() {
        InboxRequest request = request("succeeded");
        InboxStore first = firstStoreAt(INITIAL_TIME);
        InboxStore second = secondStoreAt(INITIAL_TIME);
        InboxClaim claim = first.tryClaim(request).claim().orElseThrow();

        assertEquals(InboxDecision.IN_PROGRESS, second.tryClaim(request).decision());
        first.complete(claim);
        assertEquals(InboxDecision.SUCCEEDED, second.tryClaim(request).decision());
    }

    /**
     * 验证失败消息在等待期内被拒绝，到期后增加尝试次数并重新授予租约。
     */
    @Test
    protected final void shouldRetryOnlyAfterConfiguredDelay() {
        InboxRequest request = request("retry");
        InboxClaim claim = firstStoreAt(INITIAL_TIME).tryClaim(request).claim().orElseThrow();

        firstStoreAt(INITIAL_TIME).retry(claim, Duration.ofSeconds(10), "contract-failure");

        assertEquals(
                InboxDecision.RETRY_LATER,
                secondStoreAt(INITIAL_TIME.plusSeconds(9)).tryClaim(request).decision());
        InboxAttempt retried = secondStoreAt(INITIAL_TIME.plusSeconds(11)).tryClaim(request);
        assertEquals(InboxDecision.ACQUIRED, retried.decision());
        assertEquals(2, retried.claim().orElseThrow().attempt());
    }

    /**
     * 验证租约过期后其他实例可以接管，旧所有者不能再提交状态。
     */
    @Test
    protected final void shouldRejectStaleOwnerAfterLeaseTakeover() {
        InboxRequest request = request("takeover");
        InboxClaim staleClaim = firstStoreAt(INITIAL_TIME).tryClaim(request).claim().orElseThrow();
        InboxStore newOwnerStore = secondStoreAt(INITIAL_TIME.plusSeconds(31));
        InboxClaim newClaim = newOwnerStore.tryClaim(request).claim().orElseThrow();

        assertEquals(2, newClaim.attempt());
        assertThrows(
                InboxOwnershipLostException.class,
                () -> firstStoreAt(INITIAL_TIME.plusSeconds(31)).complete(staleClaim));
        newOwnerStore.complete(newClaim);
        assertEquals(InboxDecision.SUCCEEDED, newOwnerStore.tryClaim(request).decision());
    }

    /**
     * 验证死信状态可以被识别，并可通过人工回放恢复为首次尝试。
     */
    @Test
    protected final void shouldMarkDeadAndReplayFromFirstAttempt() {
        InboxRequest request = request("dead");
        InboxStore first = firstStoreAt(INITIAL_TIME);
        long deadBefore = first.countDead(request.consumer());
        InboxClaim claim = first.tryClaim(request).claim().orElseThrow();

        first.markDead(claim, "contract-dead");

        assertEquals(InboxDecision.DEAD, secondStoreAt(INITIAL_TIME).tryClaim(request).decision());
        if (deadBefore >= 0) {
            assertEquals(deadBefore + 1, first.countDead(request.consumer()));
        }
        assertTrue(secondStoreAt(INITIAL_TIME).replayDead(request.consumer(), request.messageId()));
        assertEquals(
                1,
                secondStoreAt(INITIAL_TIME).tryClaim(request).claim().orElseThrow().attempt());
    }

    /**
     * 创建带随机消息 ID 的 Inbox 请求，避免测试状态互相污染。
     *
     * @param scenario 测试场景
     * @return Inbox 请求
     */
    private InboxRequest request(String scenario) {
        return new InboxRequest(
                "provider-contract",
                scenario + '-' + UUID.randomUUID(),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1));
    }
}
