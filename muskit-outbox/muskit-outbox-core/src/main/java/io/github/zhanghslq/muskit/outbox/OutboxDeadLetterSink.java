package io.github.zhanghslq.muskit.outbox;

/**
 * 在 Outbox 事件持久化为死信后执行外部死信通知的可替换 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface OutboxDeadLetterSink {

    /**
     * 返回不执行外部通知的默认实现。
     *
     * @return 空死信通知实现
     */
    static OutboxDeadLetterSink noop() {
        return claim -> { };
    }

    /**
     * 接收已经持久化为死信的事件租约。
     *
     * @param claim 死信事件租约
     * @throws Exception 外部死信通知失败
     */
    void publish(OutboxClaim claim) throws Exception;
}
