package io.github.zhanghslq.muskit.inbox.service;

import io.github.zhanghslq.muskit.inbox.model.InboxProcessResult;
import io.github.zhanghslq.muskit.inbox.spi.InboxHandler;
import io.github.zhanghslq.muskit.inbox.spi.InboxPolicyResolver;
import java.util.Objects;

/**
 * 使用策略名称简化业务消息可靠处理和人工回放的入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxTemplate {

    private final InboxProcessor processor;
    private final InboxPolicyResolver policyResolver;

    /**
     * 创建 Inbox 业务入口。
     *
     * @param processor Inbox 处理器
     * @param policyResolver 策略解析器
     */
    public InboxTemplate(InboxProcessor processor, InboxPolicyResolver policyResolver) {
        this.processor = Objects.requireNonNull(processor, "Inbox 处理器不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "Inbox 策略解析器不能为空");
    }

    /**
     * 按策略名称可靠处理一条业务消息。
     *
     * @param consumer 低基数消费者名称
     * @param messageId 业务消息 ID
     * @param policyName 策略名称
     * @param handler 业务处理器
     * @return 处理结果
     * @throws Exception 业务处理异常
     */
    public InboxProcessResult process(
            String consumer,
            String messageId,
            String policyName,
            InboxHandler handler) throws Exception {
        return processor.process(consumer, messageId, policyResolver.resolve(policyName), handler);
    }

    /**
     * 人工恢复一条死信消息。
     *
     * @param consumer 消费者名称
     * @param messageId 业务消息 ID
     * @return 是否恢复
     */
    public boolean replayDead(String consumer, String messageId) {
        return processor.replayDead(consumer, messageId);
    }
}
