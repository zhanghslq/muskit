package io.github.zhanghslq.muskit.example.messaging;

import io.github.zhanghslq.muskit.inbox.model.InboxProcessResult;
import io.github.zhanghslq.muskit.inbox.service.InboxTemplate;
import org.springframework.stereotype.Service;

/**
 * 演示通过业务消息 ID 可靠处理消费消息。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class ReliableMessageService {

    private final InboxTemplate inboxTemplate;

    /**
     * 创建可靠消息示例服务。
     *
     * @param inboxTemplate Inbox 模板
     */
    public ReliableMessageService(InboxTemplate inboxTemplate) {
        this.inboxTemplate = inboxTemplate;
    }

    /**
     * 可靠处理订单消息。
     *
     * @param messageId 业务消息 ID
     * @return Inbox 处理结果
     * @throws Exception 业务处理失败
     */
    public InboxProcessResult consume(String messageId) throws Exception {
        return inboxTemplate.process("order-consumer", messageId, "order-consumer", () -> processOrder());
    }

    /**
     * 执行示例订单消息业务。
     */
    private void processOrder() {
        // 示例项目不连接真实业务仓储，实际应用应在这里执行可重试的消费逻辑。
    }
}
