package io.github.zhanghslq.muskit.outbox.exception;

/**
 * Outbox 发布租约已经失效或被其他实例接管时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class OutboxOwnershipLostException extends RuntimeException {

    /**
     * 创建不包含事件标识和所有权令牌的异常。
     */
    public OutboxOwnershipLostException() {
        super("Outbox 发布租约已经失效");
    }
}
