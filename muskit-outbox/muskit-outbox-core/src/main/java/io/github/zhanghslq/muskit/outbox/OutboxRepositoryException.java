package io.github.zhanghslq.muskit.outbox;

/**
 * Outbox 持久化操作失败时抛出的统一异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class OutboxRepositoryException extends RuntimeException {

    /**
     * 使用低基数操作名称和后端原因创建异常。
     *
     * @param operation 存储操作名称
     * @param cause 后端异常
     */
    public OutboxRepositoryException(String operation, Throwable cause) {
        super("Outbox 存储操作失败，操作: " + operation, cause);
    }
}
