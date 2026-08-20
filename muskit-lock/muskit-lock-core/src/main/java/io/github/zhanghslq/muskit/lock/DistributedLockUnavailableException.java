package io.github.zhanghslq.muskit.lock;

import java.util.Objects;

/**
 * 表示分布式锁后端当前不可用。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DistributedLockUnavailableException extends RuntimeException {

    /**
     * 创建锁后端不可用异常，并移除原异常消息中可能包含的业务锁键。
     *
     * @param lockName 低基数锁名称
     * @param cause 后端异常
     */
    public DistributedLockUnavailableException(String lockName, Throwable cause) {
        super("分布式锁后端不可用，lock=" + lockName, sanitizedCause(cause));
    }

    /**
     * 仅保留后端异常类型，避免把 Redis Key 等高基数值带入公共异常链。
     *
     * @param cause 后端异常
     * @return 不包含后端异常消息的安全异常
     */
    private static Throwable sanitizedCause(Throwable cause) {
        Objects.requireNonNull(cause, "后端异常不能为空");
        return new IllegalStateException("后端异常类型：" + cause.getClass().getName());
    }
}
