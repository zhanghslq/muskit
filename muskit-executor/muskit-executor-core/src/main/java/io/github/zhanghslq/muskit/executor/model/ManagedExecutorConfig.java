package io.github.zhanghslq.muskit.executor.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 单个受管执行器的不可变配置。
 *
 * @param name 低基数执行器名称
 * @param type 线程类型
 * @param maxConcurrency 最大同时执行任务数
 * @param queueCapacity 最大等待任务数
 * @param shutdownTimeout 单独关闭时的排空超时
 * @author zhs
 * @since 2026-08-20
 */
public record ManagedExecutorConfig(
        String name,
        ExecutorType type,
        int maxConcurrency,
        int queueCapacity,
        Duration shutdownTimeout) {

    /**
     * 校验并创建受管执行器配置。
     */
    public ManagedExecutorConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("执行器名称不能为空");
        }
        if (name.length() > 128) {
            throw new IllegalArgumentException("执行器名称长度不能超过 128");
        }
        Objects.requireNonNull(type, "执行器线程类型不能为空");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("执行器最大并发数必须大于 0");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("执行器等待容量不能为负数");
        }
        Objects.requireNonNull(shutdownTimeout, "执行器关闭超时不能为空");
        if (shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("执行器关闭超时不能为负数");
        }
        try {
            Math.addExact(maxConcurrency, queueCapacity);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("执行器总容量过大", overflow);
        }
    }
}
