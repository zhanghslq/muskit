package io.github.zhanghslq.muskit.lock.autoconfigure;

/**
 * 在应用没有 MeterRegistry 时提供零开销的锁指标空实现。
 *
 * @author zhs
 * @since 2026-08-20
 */
final class NoOpLockObservation implements LockObservation {

    private static final Acquisition NO_OP_ACQUISITION = ignored -> {
    };

    /**
     * 创建锁指标空实现。
     */
    NoOpLockObservation() {
    }

    /**
     * 返回不会记录数据的指标上下文。
     *
     * @param lockName 低基数锁名称
     * @return 空指标上下文
     */
    @Override
    public Acquisition start(String lockName) {
        return NO_OP_ACQUISITION;
    }

    /**
     * 忽略本地锁降级事件。
     *
     * @param lockName 低基数锁名称
     */
    @Override
    public void fallback(String lockName) {
    }
}
