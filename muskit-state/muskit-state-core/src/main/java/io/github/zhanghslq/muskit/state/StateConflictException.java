package io.github.zhanghslq.muskit.state;

/**
 * 状态乐观锁冲突重试耗尽异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class StateConflictException extends RuntimeException {

    /**
     * 创建状态冲突异常。
     */
    public StateConflictException() {
        super("状态迁移并发冲突重试耗尽");
    }
}
