package io.github.zhanghslq.muskit.idempotency;

/**
 * 表示相同业务幂等请求已经成功完成。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyCompletedException extends RuntimeException {

    /**
     * 创建幂等已完成异常。
     *
     * @param operation 低基数操作名称
     */
    public IdempotencyCompletedException(String operation) {
        super("相同幂等请求已经完成，operation=" + operation);
    }
}
