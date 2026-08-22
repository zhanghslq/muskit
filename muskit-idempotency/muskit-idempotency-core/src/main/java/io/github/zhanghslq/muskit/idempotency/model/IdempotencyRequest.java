package io.github.zhanghslq.muskit.idempotency.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 描述一次业务幂等状态开始请求。
 *
 * @param operation 低基数操作名称
 * @param key 业务幂等键
 * @param processingTimeout 处理中状态超时时间
 * @param retention 成功状态保留时间
 * @author zhs
 * @since 2026-08-20
 */
public record IdempotencyRequest(
        String operation,
        String key,
        Duration processingTimeout,
        Duration retention) {

    /**
     * 校验并创建幂等请求。
     */
    public IdempotencyRequest {
        Objects.requireNonNull(operation, "幂等操作名称不能为空");
        Objects.requireNonNull(key, "业务幂等键不能为空");
        Objects.requireNonNull(processingTimeout, "处理超时时间不能为空");
        Objects.requireNonNull(retention, "成功状态保留时间不能为空");
        operation = operation.trim();
        if (operation.isEmpty()) {
            throw new IllegalArgumentException("幂等操作名称不能为空");
        }
        if (key.isBlank()) {
            throw new IllegalArgumentException("业务幂等键不能为空");
        }
        if (processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("处理超时时间必须为正数");
        }
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("成功状态保留时间必须为正数");
        }
    }

    /**
     * 返回不包含业务幂等键的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "IdempotencyRequest[operation=" + operation
                + ", processingTimeout=" + processingTimeout
                + ", retention=" + retention + "]";
    }
}
