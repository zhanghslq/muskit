package io.github.zhanghslq.muskit.outbox.exception;

/**
 * Outbox 后台发布被中断时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class OutboxInterruptedException extends RuntimeException {

    /**
     * 使用中断原因创建不暴露消息内容的异常。
     *
     * @param cause 中断原因
     */
    public OutboxInterruptedException(InterruptedException cause) {
        super("Outbox 后台发布被中断", cause);
    }
}
