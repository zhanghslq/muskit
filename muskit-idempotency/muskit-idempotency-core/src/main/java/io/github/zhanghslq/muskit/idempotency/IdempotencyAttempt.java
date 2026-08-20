package io.github.zhanghslq.muskit.idempotency;

import java.util.Objects;
import java.util.Optional;

/**
 * 表示幂等开始请求的判定以及成功获取时的所有权声明。
 *
 * @param decision 幂等判定
 * @param claim 成功获取时的所有权声明
 * @author zhs
 * @since 2026-08-20
 */
public record IdempotencyAttempt(IdempotencyDecision decision, Optional<IdempotencyClaim> claim) {

    /**
     * 校验并创建幂等尝试结果。
     */
    public IdempotencyAttempt {
        Objects.requireNonNull(decision, "幂等判定不能为空");
        Objects.requireNonNull(claim, "幂等所有权结果不能为空");
        if ((decision == IdempotencyDecision.ACQUIRED) != claim.isPresent()) {
            throw new IllegalArgumentException("只有 ACQUIRED 判定可以携带所有权声明");
        }
    }

    /**
     * 创建成功获取所有权的结果。
     *
     * @param claim 幂等所有权声明
     * @return 成功获取结果
     */
    public static IdempotencyAttempt acquired(IdempotencyClaim claim) {
        return new IdempotencyAttempt(IdempotencyDecision.ACQUIRED, Optional.of(claim));
    }

    /**
     * 创建未获取所有权的结果。
     *
     * @param decision IN_PROGRESS 或 COMPLETED 判定
     * @return 未获取结果
     */
    public static IdempotencyAttempt rejected(IdempotencyDecision decision) {
        if (decision == IdempotencyDecision.ACQUIRED) {
            throw new IllegalArgumentException("ACQUIRED 判定必须携带所有权声明");
        }
        return new IdempotencyAttempt(decision, Optional.empty());
    }
}
