package io.github.zhanghslq.muskit.audit.exception;

/**
 * 审计事件写入失败异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class AuditWriteException extends RuntimeException {

    /**
     * 创建审计写入异常。
     *
     * @param cause 原始异常
     */
    public AuditWriteException(Throwable cause) {
        super("审计事件写入失败", cause);
    }
}
