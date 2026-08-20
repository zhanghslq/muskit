package io.github.zhanghslq.muskit.outbox;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * 在业务事务中创建并追加 Outbox 事件的应用入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxService {

    private final OutboxRepository repository;
    private final Clock clock;

    /**
     * 使用 UTC 系统时钟创建 Outbox 服务。
     *
     * @param repository Outbox 存储
     */
    public OutboxService(OutboxRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建 Outbox 服务。
     *
     * @param repository Outbox 存储
     * @param clock 事件创建时钟
     */
    public OutboxService(OutboxRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Outbox 存储不能为空");
        this.clock = Objects.requireNonNull(clock, "Outbox 时钟不能为空");
    }

    /**
     * 创建事件并在当前业务事务中写入 Outbox 表。
     *
     * @param request 消息请求
     * @return 内部事件标识
     */
    public UUID publish(OutboxMessageRequest request) {
        Objects.requireNonNull(request, "Outbox 消息请求不能为空");
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                request.destination(),
                request.key(),
                request.payload(),
                request.headers(),
                clock.instant());
        repository.append(event);
        return event.id();
    }
}
