package io.github.zhanghslq.muskit.resilience.deadline;

/**
 * 当前调用链 Deadline 已到期时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class DeadlineExceededException extends RuntimeException {

    /**
     * 创建不暴露业务信息的 Deadline 超时异常。
     */
    public DeadlineExceededException() {
        super("调用 Deadline 已到期");
    }

    /**
     * 创建保留最近业务失败原因的 Deadline 超时异常。
     *
     * @param cause 最近一次业务失败
     */
    public DeadlineExceededException(Throwable cause) {
        super("剩余 Deadline 不足以执行下一次重试", cause);
    }
}
