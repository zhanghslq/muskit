package io.github.zhanghslq.muskit.outbox;

/**
 * 一次 Outbox 批量发布的低基数汇总结果。
 *
 * @param claimed 取得租约的事件数
 * @param published 成功发布数
 * @param failed 发布失败并等待重试数
 * @author zhs
 * @since 2026-08-20
 */
public record OutboxDispatchReport(int claimed, int published, int failed) {

    /**
     * 校验并创建发布汇总。
     */
    public OutboxDispatchReport {
        if (claimed < 0 || published < 0 || failed < 0 || published + failed > claimed) {
            throw new IllegalArgumentException("Outbox 发布汇总数量无效");
        }
    }
}
