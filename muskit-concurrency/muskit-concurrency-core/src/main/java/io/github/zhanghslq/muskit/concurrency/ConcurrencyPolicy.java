package io.github.zhanghslq.muskit.concurrency;

import java.time.Duration;
import java.util.Objects;

/**
 * 不可变的并发控制策略。
 *
 * @param name 策略名称
 * @param maxConcurrency 最大并发数
 * @param maxWait 最大等待时间
 * @param scope 隔离范围
 * @param fair 是否使用公平获取顺序
 * @author zhs
 * @since 2026-08-20
 */
public record ConcurrencyPolicy(
        String name,
        int maxConcurrency,
        Duration maxWait,
        ConcurrencyScope scope,
        boolean fair) {

    /**
     * 校验并创建并发控制策略。
     */
    public ConcurrencyPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("并发策略名称不能为空");
        }
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("最大并发数必须大于 0");
        }
        Objects.requireNonNull(maxWait, "最大等待时间不能为空");
        if (maxWait.isNegative()) {
            throw new IllegalArgumentException("最大等待时间不能小于 0");
        }
        Objects.requireNonNull(scope, "并发隔离范围不能为空");
    }
}

