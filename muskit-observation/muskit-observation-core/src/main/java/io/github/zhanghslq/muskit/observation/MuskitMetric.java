package io.github.zhanghslq.muskit.observation;

/**
 * Muskit 全局稳定指标目录。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum MuskitMetric {

    /** 分布式锁获取耗时。 */
    LOCK_ACQUIRE("muskit.lock.acquire", MuskitMetricKind.TIMER),

    /** 分布式锁显式本地降级次数。 */
    LOCK_FALLBACK("muskit.lock.fallback", MuskitMetricKind.COUNTER),

    /** 幂等业务执行次数。 */
    IDEMPOTENCY_EXECUTE("muskit.idempotency.execute", MuskitMetricKind.COUNTER),

    /** 幂等完成态重复次数。 */
    IDEMPOTENCY_DUPLICATE("muskit.idempotency.duplicate", MuskitMetricKind.COUNTER),

    /** 幂等处理中冲突次数。 */
    IDEMPOTENCY_IN_PROGRESS("muskit.idempotency.in.progress", MuskitMetricKind.COUNTER),

    /** 并发许可获取耗时。 */
    CONCURRENCY_ACQUIRE("muskit.concurrency.acquire", MuskitMetricKind.TIMER),

    /** 并发许可拒绝次数。 */
    CONCURRENCY_REJECTED("muskit.concurrency.rejected", MuskitMetricKind.COUNTER),

    /** 当前并发执行数量。 */
    CONCURRENCY_INFLIGHT("muskit.concurrency.inflight", MuskitMetricKind.GAUGE),

    /** 限流请求判定次数。 */
    RATE_LIMIT_REQUEST("muskit.ratelimit.request", MuskitMetricKind.COUNTER),

    /** 限流拒绝次数。 */
    RATE_LIMIT_REJECTED("muskit.ratelimit.rejected", MuskitMetricKind.COUNTER),

    /** 重试实际尝试次数。 */
    RETRY_ATTEMPT("muskit.retry.attempt", MuskitMetricKind.COUNTER),

    /** 重试耗尽次数。 */
    RETRY_EXHAUSTED("muskit.retry.exhausted", MuskitMetricKind.COUNTER),

    /** 熔断保护调用耗时。 */
    CIRCUIT_BREAKER_CALL("muskit.circuitbreaker.call", MuskitMetricKind.TIMER),

    /** Outbox 待发布积压数量。 */
    OUTBOX_PENDING("muskit.outbox.pending", MuskitMetricKind.GAUGE),

    /** Outbox 发布结果次数。 */
    OUTBOX_PUBLISH("muskit.outbox.publish", MuskitMetricKind.COUNTER),

    /** Outbox 进入重试次数。 */
    OUTBOX_RETRY("muskit.outbox.retry", MuskitMetricKind.COUNTER),

    /** Outbox 进入死信次数。 */
    OUTBOX_DEAD("muskit.outbox.dead", MuskitMetricKind.COUNTER),

    /** Inbox 消息处理次数。 */
    INBOX_PROCESS("muskit.inbox.process", MuskitMetricKind.COUNTER),

    /** Inbox 死信数量。 */
    INBOX_DEAD("muskit.inbox.dead", MuskitMetricKind.COUNTER),

    /** 受管执行器任务次数。 */
    EXECUTOR_TASK("muskit.executor.task", MuskitMetricKind.COUNTER),

    /** 受管执行器当前任务数。 */
    EXECUTOR_INFLIGHT("muskit.executor.inflight", MuskitMetricKind.GAUGE),

    /** 缓存查询结果次数。 */
    CACHE_LOOKUP("muskit.cache.lookup", MuskitMetricKind.COUNTER),

    /** 外部客户端调用耗时。 */
    CLIENT_CALL("muskit.client.call", MuskitMetricKind.TIMER),

    /** 审计事件写入结果次数。 */
    AUDIT_WRITE("muskit.audit.write", MuskitMetricKind.COUNTER);

    private final String metricName;
    private final MuskitMetricKind kind;

    /**
     * 创建稳定指标定义。
     *
     * @param metricName Micrometer 指标名称
     * @param kind 指标类型
     */
    MuskitMetric(String metricName, MuskitMetricKind kind) {
        this.metricName = metricName;
        this.kind = kind;
    }

    /**
     * 返回指标名称。
     *
     * @return 指标名称
     */
    public String metricName() {
        return metricName;
    }

    /**
     * 返回指标类型。
     *
     * @return 指标类型
     */
    public MuskitMetricKind kind() {
        return kind;
    }
}
