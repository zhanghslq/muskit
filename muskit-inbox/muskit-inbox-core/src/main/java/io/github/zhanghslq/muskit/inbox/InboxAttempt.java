package io.github.zhanghslq.muskit.inbox;

import java.time.Duration;
import java.util.Optional;

/**
 * Inbox 原子竞争的判定结果。
 *
 * @param decision 判定类型
 * @param claim 获得的处理租约
 * @param retryAfter 尚需等待的重试时间
 * @author zhs
 * @since 2026-08-20
 */
public record InboxAttempt(
        InboxDecision decision,
        Optional<InboxClaim> claim,
        Duration retryAfter) {

    /**
     * 校验并创建 Inbox 判定结果。
     */
    public InboxAttempt {
        if (decision == null || claim == null || retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("Inbox 判定结果参数无效");
        }
        if ((decision == InboxDecision.ACQUIRED) != claim.isPresent()) {
            throw new IllegalArgumentException("只有 ACQUIRED 判定可以携带 Inbox 租约");
        }
    }

    /**
     * 创建获得租约的判定结果。
     *
     * @param claim 处理租约
     * @return 判定结果
     */
    public static InboxAttempt acquired(InboxClaim claim) {
        return new InboxAttempt(InboxDecision.ACQUIRED, Optional.of(claim), Duration.ZERO);
    }

    /**
     * 创建不携带租约的判定结果。
     *
     * @param decision 判定类型
     * @param retryAfter 重试等待时间
     * @return 判定结果
     */
    public static InboxAttempt rejected(InboxDecision decision, Duration retryAfter) {
        return new InboxAttempt(decision, Optional.empty(), retryAfter);
    }
}
