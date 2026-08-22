package io.github.zhanghslq.muskit.idempotency.exception;

import java.util.Objects;

/**
 * 表示幂等状态存储当前不可用。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyStoreException extends RuntimeException {

    /**
     * 创建幂等存储异常，并移除原异常消息中的业务键等敏感内容。
     *
     * @param operation 低基数操作名称
     * @param cause 存储异常
     */
    public IdempotencyStoreException(String operation, Throwable cause) {
        super("幂等状态存储不可用，operation=" + operation, sanitizedCause(cause));
    }

    /**
     * 仅保留后端异常类型。
     *
     * @param cause 后端异常
     * @return 安全异常
     */
    private static Throwable sanitizedCause(Throwable cause) {
        Objects.requireNonNull(cause, "存储异常不能为空");
        return new IllegalStateException("后端异常类型：" + cause.getClass().getName());
    }
}
