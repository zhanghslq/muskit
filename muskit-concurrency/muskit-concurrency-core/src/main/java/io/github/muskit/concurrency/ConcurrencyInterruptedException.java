package io.github.muskit.concurrency;

/**
 * 等待并发额度期间当前线程被中断时抛出的异常。
 *
 * @author zhs
 * @since 2026-08-20
 */
public class ConcurrencyInterruptedException extends RuntimeException {

    /**
     * 根据策略名称和原始中断异常创建异常。
     *
     * @param policyName 策略名称
     * @param cause 原始中断异常
     */
    public ConcurrencyInterruptedException(String policyName, InterruptedException cause) {
        super("等待并发额度时被中断，策略: " + policyName, cause);
    }
}

