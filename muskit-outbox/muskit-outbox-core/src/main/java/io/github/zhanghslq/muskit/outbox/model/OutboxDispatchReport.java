package io.github.zhanghslq.muskit.outbox.model;

import java.util.Objects;

/**
 * 一次 Outbox 批量发布的低基数汇总结果。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxDispatchReport {

    private final int claimed;
    private final int published;
    private final int failed;
    private final int dead;

    /**
     * 创建不包含死信数量的兼容发布汇总。
     *
     * @param claimed 取得租约的事件数
     * @param published 成功发布数
     * @param failed 发布失败数
     */
    public OutboxDispatchReport(int claimed, int published, int failed) {
        this(claimed, published, failed, 0);
    }

    /**
     * 创建包含死信数量的发布汇总。
     *
     * @param claimed 取得租约的事件数
     * @param published 成功发布数
     * @param failed 发布失败总数，包括死信
     * @param dead 本批进入死信的事件数
     */
    public OutboxDispatchReport(int claimed, int published, int failed, int dead) {
        if (claimed < 0 || published < 0 || failed < 0 || dead < 0
                || published + failed > claimed || dead > failed) {
            throw new IllegalArgumentException("Outbox 发布汇总数量无效");
        }
        this.claimed = claimed;
        this.published = published;
        this.failed = failed;
        this.dead = dead;
    }

    /**
     * 返回取得租约的事件数。
     *
     * @return 事件数
     */
    public int claimed() {
        return claimed;
    }

    /**
     * 返回成功发布数。
     *
     * @return 成功数
     */
    public int published() {
        return published;
    }

    /**
     * 返回发布失败总数。
     *
     * @return 失败数
     */
    public int failed() {
        return failed;
    }

    /**
     * 返回本批进入死信的事件数。
     *
     * @return 死信数
     */
    public int dead() {
        return dead;
    }

    /**
     * 按汇总字段判断相等。
     *
     * @param other 待比较对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof OutboxDispatchReport that
                && claimed == that.claimed
                && published == that.published
                && failed == that.failed
                && dead == that.dead;
    }

    /**
     * 返回汇总字段哈希。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(claimed, published, failed, dead);
    }

    /**
     * 返回低基数汇总描述。
     *
     * @return 汇总描述
     */
    @Override
    public String toString() {
        return "OutboxDispatchReport[claimed=" + claimed + ", published=" + published
                + ", failed=" + failed + ", dead=" + dead + ']';
    }
}
