package io.github.zhanghslq.muskit.idempotency.exception;

/**
 * 表示相同业务幂等请求仍在处理中。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyInProgressException extends RuntimeException {

    /**
     * 创建幂等处理中异常。
     *
     * @param operation 低基数操作名称
     */
    public IdempotencyInProgressException(String operation) {
        super("相同幂等请求正在处理中，operation=" + operation);
    }
}
