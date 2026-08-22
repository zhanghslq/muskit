package io.github.zhanghslq.muskit.outbox.exception;

/**
 * 表示 Outbox 死信状态已提交，但外部死信通知失败。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxDeadLetterNotificationException extends RuntimeException {

    /**
     * 创建死信通知异常。
     *
     * @param cause 外部通知异常
     */
    public OutboxDeadLetterNotificationException(Throwable cause) {
        super("Outbox 事件已进入死信状态，但外部死信通知失败", cause);
    }
}
