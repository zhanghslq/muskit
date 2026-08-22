package io.github.zhanghslq.muskit.outbox.spi;

import io.github.zhanghslq.muskit.outbox.model.OutboxEvent;

/**
 * 将 Outbox 事件发送到具体消息系统的扩展点。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface OutboxPublisher {

    /**
     * 发布单个事件，只有消息系统确认后才应正常返回。
     *
     * @param event Outbox 事件
     * @throws Exception 消息系统发布失败
     */
    void publish(OutboxEvent event) throws Exception;
}
