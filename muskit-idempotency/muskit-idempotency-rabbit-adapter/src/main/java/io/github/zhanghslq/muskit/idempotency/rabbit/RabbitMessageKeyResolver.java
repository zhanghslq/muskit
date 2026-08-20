package io.github.zhanghslq.muskit.idempotency.rabbit;

import org.springframework.amqp.core.Message;

/**
 * 从 RabbitMQ 原始消息中解析稳定幂等键的策略接口。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RabbitMessageKeyResolver {

    /**
     * 解析消息幂等键。
     *
     * @param message RabbitMQ 原始消息
     * @return 稳定幂等键
     */
    String resolve(Message message);
}
