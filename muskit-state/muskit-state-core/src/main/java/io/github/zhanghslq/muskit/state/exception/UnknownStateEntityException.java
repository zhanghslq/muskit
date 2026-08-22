package io.github.zhanghslq.muskit.state.exception;

/**
 * 状态仓储中不存在目标实体异常，不在消息中暴露实体标识。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class UnknownStateEntityException extends RuntimeException {

    /**
     * 创建状态实体不存在异常。
     */
    public UnknownStateEntityException() {
        super("状态实体不存在");
    }
}
