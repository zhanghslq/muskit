package io.github.zhanghslq.muskit.executor;

import java.util.concurrent.RejectedExecutionException;

/**
 * 表示受管执行器按明确原因拒绝了新任务。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ManagedTaskRejectedException extends RejectedExecutionException {

    private final String executorName;
    private final TaskRejectionReason reason;

    /**
     * 创建任务拒绝异常，不携带任务参数或上下文值。
     *
     * @param executorName 低基数执行器名称
     * @param reason 拒绝原因
     */
    public ManagedTaskRejectedException(String executorName, TaskRejectionReason reason) {
        super("受管执行器拒绝任务: executor=" + executorName + ", reason=" + reason);
        this.executorName = executorName;
        this.reason = reason;
    }

    /**
     * 返回执行器名称。
     *
     * @return 执行器名称
     */
    public String executorName() {
        return executorName;
    }

    /**
     * 返回拒绝原因。
     *
     * @return 拒绝原因
     */
    public TaskRejectionReason reason() {
        return reason;
    }
}
