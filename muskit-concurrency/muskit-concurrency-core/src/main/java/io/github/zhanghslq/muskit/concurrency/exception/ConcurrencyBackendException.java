package io.github.zhanghslq.muskit.concurrency.exception;

/**
 * 并发额度后端不可用或状态操作失败时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class ConcurrencyBackendException extends RuntimeException {

    /**
     * 使用低基数策略名称创建并发后端异常。
     *
     * @param policyName 并发策略名称
     * @param cause 后端异常，仅保留类型而不透传可能包含业务键的消息
     */
    public ConcurrencyBackendException(String policyName, Throwable cause) {
        super("并发额度后端操作失败，策略: " + policyName
                + "，原因类型: " + cause.getClass().getSimpleName(), cause);
    }
}
