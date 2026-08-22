package io.github.zhanghslq.muskit.idempotency.model;

/**
 * 不包含业务 ID 的批量幂等处理汇总。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyBatchReport {

    private final int completed;
    private final int duplicate;
    private final int inProgress;

    /**
     * 创建批量幂等处理汇总。
     *
     * @param completed 本次成功处理数量
     * @param duplicate 已完成重复数量
     * @param inProgress 处理中冲突数量
     */
    public IdempotencyBatchReport(int completed, int duplicate, int inProgress) {
        if (completed < 0 || duplicate < 0 || inProgress < 0) {
            throw new IllegalArgumentException("批量幂等统计不能为负数");
        }
        this.completed = completed;
        this.duplicate = duplicate;
        this.inProgress = inProgress;
    }

    /**
     * 返回本次成功处理数量。
     *
     * @return 成功数量
     */
    public int completed() {
        return completed;
    }

    /**
     * 返回已完成重复数量。
     *
     * @return 重复数量
     */
    public int duplicate() {
        return duplicate;
    }

    /**
     * 返回处理中冲突数量。
     *
     * @return 冲突数量
     */
    public int inProgress() {
        return inProgress;
    }
}
