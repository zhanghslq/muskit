package io.github.zhanghslq.muskit.executor;

import io.github.zhanghslq.muskit.lifecycle.DrainState;

/**
 * 受管执行器不包含任务内容的状态快照。
 *
 * @param name 执行器名称
 * @param type 线程类型
 * @param state 排空状态
 * @param inflight 已接收且尚未结束的任务数
 * @param availableCapacity 剩余接收容量
 * @author zhs
 * @since 2026-08-20
 */
public record ManagedExecutorSnapshot(
        String name,
        ExecutorType type,
        DrainState state,
        long inflight,
        int availableCapacity) {
}
