package io.github.muskit.concurrency;

/**
 * 根据名称解析并发控制策略的 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface ConcurrencyPolicyResolver {

    /**
     * 解析指定名称的并发控制策略。
     *
     * @param policyName 策略名称
     * @return 并发控制策略
     * @throws UnknownConcurrencyPolicyException 策略不存在
     */
    ConcurrencyPolicy resolve(String policyName);
}

