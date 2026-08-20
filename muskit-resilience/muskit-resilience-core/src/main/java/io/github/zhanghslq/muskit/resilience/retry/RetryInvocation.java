package io.github.zhanghslq.muskit.resilience.retry;

/**
 * 允许抛出任意业务异常的单次重试调用。
 *
 * @param <T> 调用结果类型
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface RetryInvocation<T> {

    /**
     * 执行一次业务调用。
     *
     * @return 调用结果
     * @throws Throwable 业务异常
     */
    T invoke() throws Throwable;
}
