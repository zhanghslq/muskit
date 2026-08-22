package io.github.zhanghslq.muskit.inbox.exception;

/**
 * 表示 Inbox 后端操作失败。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InboxStoreException extends RuntimeException {

    /**
     * 创建不包含业务消息 ID 的存储异常。
     *
     * @param operation 低基数存储操作名称
     * @param cause 后端异常
     */
    public InboxStoreException(String operation, Throwable cause) {
        super("Inbox 存储操作失败: operation=" + operation, cause);
    }
}
