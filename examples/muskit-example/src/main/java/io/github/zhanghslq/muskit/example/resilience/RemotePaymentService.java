package io.github.zhanghslq.muskit.example.resilience;

import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerGuard;
import io.github.zhanghslq.muskit.resilience.retry.RetryGuard;
import org.springframework.stereotype.Service;

/**
 * 演示 Retry 和 Circuit Breaker 共同保护远程调用。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class RemotePaymentService {

    /**
     * 创建远程支付示例服务。
     */
    public RemotePaymentService() {
    }

    /**
     * 使用同名策略执行受重试和熔断保护的支付调用。
     *
     * @param paymentId 支付标识
     * @return 支付结果
     */
    @RetryGuard(policy = "remote-payment")
    @CircuitBreakerGuard(policy = "remote-payment")
    public String pay(String paymentId) {
        return "paid:" + paymentId;
    }
}
