package io.github.zhanghslq.muskit.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 按共享超时预算协调多个组件同时开始并依次等待排空。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainCoordinator {

    private final List<Drainable> components;

    /**
     * 创建排空协调器并拒绝重复组件名称。
     *
     * @param components 可排空组件
     */
    public DrainCoordinator(List<? extends Drainable> components) {
        Objects.requireNonNull(components, "可排空组件不能为空");
        Set<String> names = new HashSet<>();
        for (Drainable component : components) {
            Objects.requireNonNull(component, "可排空组件不能包含 null");
            if (!names.add(component.name())) {
                throw new IllegalArgumentException("可排空组件名称重复: " + component.name());
            }
        }
        this.components = List.copyOf(components);
    }

    /**
     * 通知所有组件立即停止接受新工作。
     */
    public void beginDrain() {
        components.forEach(Drainable::beginDrain);
    }

    /**
     * 先同时触发排空，再按一个共享超时预算等待全部组件完成。
     *
     * @param timeout 全局最大等待时间
     * @return 排空汇总结果
     */
    public DrainReport drain(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("全局排空等待时间不能为负数");
        }
        beginDrain();
        long timeoutNanos = saturatedNanos(timeout);
        long startedAt = System.nanoTime();
        List<String> incomplete = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            long elapsed = Math.max(0L, System.nanoTime() - startedAt);
            long remaining = Math.max(0L, timeoutNanos - elapsed);
            Drainable component = components.get(index);
            if (!component.awaitDrained(Duration.ofNanos(remaining))) {
                incomplete.add(component.name());
                // 共享预算耗尽后仍逐个执行零等待检查，报告所有未排空组件。
                for (int pendingIndex = index + 1; pendingIndex < components.size(); pendingIndex++) {
                    Drainable pending = components.get(pendingIndex);
                    if (!pending.awaitDrained(Duration.ZERO)) {
                        incomplete.add(pending.name());
                    }
                }
                break;
            }
        }
        return new DrainReport(incomplete.isEmpty(), incomplete);
    }

    /**
     * 返回参与全局排空的组件快照。
     *
     * @return 可排空组件
     */
    public List<Drainable> components() {
        return components;
    }

    /**
     * 将超时转换为饱和纳秒数。
     *
     * @param timeout 超时时间
     * @return 纳秒值
     */
    private long saturatedNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
