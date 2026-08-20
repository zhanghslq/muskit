package io.github.zhanghslq.muskit.lock.autoconfigure;

/**
 * 定义分布式锁低基数指标的记录边界。
 *
 * @author zhs
 * @since 2026-08-20
 */
interface LockObservation {

    /**
     * 开始记录一次锁获取。
     *
     * @param lockName 低基数锁名称
     * @return 本次锁获取的指标上下文
     */
    Acquisition start(String lockName);

    /**
     * 记录一次从 Redis 降级到本地锁的事件。
     *
     * @param lockName 低基数锁名称
     */
    void fallback(String lockName);

    /**
     * 表示一次锁获取的指标上下文。
     *
     * @author zhs
     * @since 2026-08-20
     */
    @FunctionalInterface
    interface Acquisition {

        /**
         * 使用最终结果完成本次指标记录。
         *
         * @param outcome 锁获取结果
         */
        void complete(Outcome outcome);
    }

    /**
     * 锁获取指标允许使用的低基数结果。
     *
     * @author zhs
     * @since 2026-08-20
     */
    enum Outcome {

        ACQUIRED("acquired"),
        REJECTED("rejected"),
        INTERRUPTED("interrupted"),
        ERROR("error");

        private final String tagValue;

        /**
         * 创建锁获取结果。
         *
         * @param tagValue 指标标签值
         */
        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }

        /**
         * 返回指标使用的低基数标签值。
         *
         * @return 指标标签值
         */
        String tagValue() {
            return tagValue;
        }
    }
}
