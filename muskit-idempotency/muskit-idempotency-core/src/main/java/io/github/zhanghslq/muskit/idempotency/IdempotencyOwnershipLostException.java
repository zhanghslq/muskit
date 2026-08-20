package io.github.zhanghslq.muskit.idempotency;

/**
 * 表示处理中记录超时或被其他调用接管，当前调用不再拥有状态变更权限。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyOwnershipLostException extends RuntimeException {

    /**
     * 创建幂等所有权丢失异常。
     *
     * @param operation 低基数操作名称
     */
    public IdempotencyOwnershipLostException(String operation) {
        super("幂等处理中记录的所有权已经失效，operation=" + operation);
    }
}
