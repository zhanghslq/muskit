package io.github.zhanghslq.muskit.resilience.retry;

/**
 * 对异常执行额外重试判定的扩展点。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RetryPredicate {

    /**
     * 判断指定异常是否允许重试。
     *
     * @param failure 调用异常
     * @return 是否允许重试
     */
    boolean shouldRetry(Throwable failure);

    /**
     * 返回允许所有异常继续参与类型规则判定的默认实现。
     *
     * @return 默认判定器
     */
    static RetryPredicate always() {
        return failure -> true;
    }
}
