package io.github.zhanghslq.muskit.executor.service;

import io.github.zhanghslq.muskit.lifecycle.service.DrainCoordinator;
import io.github.zhanghslq.muskit.lifecycle.spi.Drainable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 按低基数名称管理多个受管执行器，并作为整体参与应用排空。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ManagedExecutorRegistry implements Drainable, AutoCloseable {

    private final Map<String, ManagedTaskExecutor> executors;

    /**
     * 创建执行器注册表并拒绝重复名称。
     *
     * @param executors 受管执行器列表
     */
    public ManagedExecutorRegistry(List<ManagedTaskExecutor> executors) {
        Objects.requireNonNull(executors, "受管执行器列表不能为空");
        Map<String, ManagedTaskExecutor> indexed = new LinkedHashMap<>();
        for (ManagedTaskExecutor executor : executors) {
            Objects.requireNonNull(executor, "受管执行器列表不能包含 null");
            if (indexed.putIfAbsent(executor.name(), executor) != null) {
                throw new IllegalArgumentException("受管执行器名称重复: " + executor.name());
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个受管执行器");
        }
        this.executors = Map.copyOf(indexed);
    }

    /**
     * 返回整体排空组件名称。
     *
     * @return 组件名称
     */
    @Override
    public String name() {
        return "managed-executors";
    }

    /**
     * 按名称返回受管执行器。
     *
     * @param name 执行器名称
     * @return 受管执行器
     */
    public ManagedTaskExecutor get(String name) {
        ManagedTaskExecutor executor = executors.get(name);
        if (executor == null) {
            throw new IllegalArgumentException("未知受管执行器: " + name);
        }
        return executor;
    }

    /**
     * 返回默认受管执行器。
     *
     * @return 名为 default 的执行器
     */
    public ManagedTaskExecutor defaultExecutor() {
        return get("default");
    }

    /**
     * 返回已配置的低基数执行器名称。
     *
     * @return 执行器名称集合
     */
    public Set<String> names() {
        return executors.keySet();
    }

    /**
     * 通知全部执行器停止接收新任务。
     */
    @Override
    public void beginDrain() {
        executors.values().forEach(ManagedTaskExecutor::beginDrain);
    }

    /**
     * 使用共享超时预算等待全部执行器排空。
     *
     * @param timeout 最大等待时间
     * @return 是否全部完成
     */
    @Override
    public boolean awaitDrained(Duration timeout) {
        return new DrainCoordinator(List.copyOf(executors.values())).drain(timeout).completed();
    }

    /**
     * 关闭全部执行器；某个关闭失败时仍继续关闭其余执行器。
     */
    @Override
    public void close() {
        RuntimeException aggregate = null;
        for (ManagedTaskExecutor executor : executors.values()) {
            try {
                executor.close();
            } catch (RuntimeException failure) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("关闭受管执行器失败");
                }
                aggregate.addSuppressed(failure);
            }
        }
        if (aggregate != null) {
            throw aggregate;
        }
    }
}
