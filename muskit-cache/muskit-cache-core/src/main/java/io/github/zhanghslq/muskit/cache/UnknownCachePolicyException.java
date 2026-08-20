package io.github.zhanghslq.muskit.cache;

/**
 * 表示业务引用了不存在的缓存策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class UnknownCachePolicyException extends RuntimeException {

    /**
     * 创建未知缓存策略异常。
     *
     * @param policyName 策略名称
     */
    public UnknownCachePolicyException(String policyName) {
        super("未知缓存策略: " + policyName);
    }
}
