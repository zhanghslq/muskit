package io.github.zhanghslq.muskit.idempotency;

/**
 * 定义原子幂等状态存储能力。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface IdempotencyStore {

    /**
     * 原子尝试创建处理中状态或读取已有状态。
     *
     * @param request 幂等请求
     * @return 幂等尝试结果
     */
    IdempotencyAttempt tryStart(IdempotencyRequest request);

    /**
     * 仅由当前所有者将处理中状态转换为成功状态。
     *
     * @param claim 幂等所有权声明
     */
    void complete(IdempotencyClaim claim);

    /**
     * 业务失败时仅由当前所有者释放处理中状态，使后续调用可以重试。
     *
     * @param claim 幂等所有权声明
     */
    void release(IdempotencyClaim claim);
}
