package io.github.zhanghslq.muskit.inbox;

/**
 * 表示 Inbox 状态更新时当前处理租约已经失效或被接管。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxOwnershipLostException extends RuntimeException {

    /**
     * 创建不暴露消息 ID 的所有权丢失异常。
     */
    public InboxOwnershipLostException() {
        super("Inbox 消息处理所有权已经失效");
    }
}
