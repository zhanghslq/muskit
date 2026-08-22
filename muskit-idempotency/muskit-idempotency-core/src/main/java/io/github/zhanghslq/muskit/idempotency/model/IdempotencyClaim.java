package io.github.zhanghslq.muskit.idempotency.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 表示当前调用对一条处理中幂等记录的所有权。
 *
 * @param operation 低基数操作名称
 * @param key 业务幂等键
 * @param ownerToken 所有权令牌
 * @param retention 成功状态保留时间
 * @author zhs
 * @since 2026-08-20
 */
public record IdempotencyClaim(String operation, String key, String ownerToken, Duration retention) {

    /**
     * 校验并创建幂等所有权声明。
     */
    public IdempotencyClaim {
        Objects.requireNonNull(operation, "幂等操作名称不能为空");
        Objects.requireNonNull(key, "业务幂等键不能为空");
        Objects.requireNonNull(ownerToken, "所有权令牌不能为空");
        Objects.requireNonNull(retention, "成功状态保留时间不能为空");
    }

    /**
     * 返回不包含业务幂等键和所有权令牌的安全描述。
     *
     * @return 安全描述
     */
    @Override
    public String toString() {
        return "IdempotencyClaim[operation=" + operation + ", retention=" + retention + "]";
    }
}
