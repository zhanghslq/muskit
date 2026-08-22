package io.github.zhanghslq.muskit.inbox.spi;

/**
 * 允许抛出受检异常的 Inbox 业务消息处理器。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface InboxHandler {

    /**
     * 处理一条业务消息。
     *
     * @throws Exception 业务处理异常
     */
    void handle() throws Exception;
}
