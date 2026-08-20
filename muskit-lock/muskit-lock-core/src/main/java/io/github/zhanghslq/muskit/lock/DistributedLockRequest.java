package io.github.zhanghslq.muskit.lock;

import java.time.Duration;
import java.util.Objects;

/**
 * 描述一次锁获取请求，零租约表示由具体分布式实现负责自动续期。
 *
 * @param name 低基数锁名称
 * @param key 业务锁键
 * @param waitTime 最长等待时间
 * @param leaseTime 固定租约时间，零表示自动续期
 * @param fair 是否公平获取
 * @param localFallback Redis 异常时是否允许本地锁降级
 * @author zhs
 * @since 2026-08-20
 */
public record DistributedLockRequest(
        String name,
        String key,
        Duration waitTime,
        Duration leaseTime,
        boolean fair,
        boolean localFallback) {

    /**
     * 校验并创建锁请求。
     */
    public DistributedLockRequest {
        Objects.requireNonNull(name, "锁名称不能为空");
        Objects.requireNonNull(key, "业务锁键不能为空");
        Objects.requireNonNull(waitTime, "等待时间不能为空");
        Objects.requireNonNull(leaseTime, "租约时间不能为空");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("锁名称不能为空");
        }
        if (waitTime.isNegative()) {
            throw new IllegalArgumentException("等待时间不能为负数");
        }
        if (leaseTime.isNegative()) {
            throw new IllegalArgumentException("租约时间不能为负数");
        }
    }

    /**
     * 返回不包含业务锁键的安全描述，避免敏感或高基数值被意外记录。
     *
     * @return 安全的锁请求描述
     */
    @Override
    public String toString() {
        return "DistributedLockRequest[name=" + name
                + ", waitTime=" + waitTime
                + ", leaseTime=" + leaseTime
                + ", fair=" + fair
                + ", localFallback=" + localFallback + "]";
    }
}
