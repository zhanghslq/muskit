package io.github.zhanghslq.muskit.concurrency;

/**
 * 在限定时间内无法获取并发额度时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class ConcurrencyRejectedException extends RuntimeException {

    /** 触发并发拒绝的策略名称。 */
    private final String policyName;

    /**
     * 根据策略名称创建并发拒绝异常。
     *
     * @param policyName 策略名称
     */
    public ConcurrencyRejectedException(String policyName) {
        super("并发额度获取超时，策略: " + policyName);
        this.policyName = policyName;
    }

    /**
     * 返回触发拒绝的策略名称。
     *
     * @return 策略名称
     */
    public String getPolicyName() {
        return policyName;
    }
}
