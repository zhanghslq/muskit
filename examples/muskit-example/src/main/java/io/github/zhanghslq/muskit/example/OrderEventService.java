package io.github.zhanghslq.muskit.example;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.zhanghslq.muskit.outbox.OutboxMessageRequest;
import io.github.zhanghslq.muskit.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示在本地业务事务中追加 Kafka Outbox 事件。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class OrderEventService {

    private final OutboxService outboxService;

    /**
     * 创建订单事件示例服务。
     *
     * @param outboxService Outbox 服务
     */
    public OrderEventService(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * 在当前业务事务中记录待发布的订单创建事件。
     *
     * @param orderId 订单标识
     */
    @Transactional
    public void recordCreated(String orderId) {
        outboxService.publish(new OutboxMessageRequest(
                "orders",
                orderId,
                "{\"status\":\"created\"}".getBytes(StandardCharsets.UTF_8),
                Map.of("event-type", "order-created")));
    }
}
