package io.github.zhanghslq.muskit.example;

import java.util.concurrent.TimeUnit;

import io.github.zhanghslq.muskit.idempotency.Idempotent;
import io.github.zhanghslq.muskit.lock.DistributedLock;
import io.github.zhanghslq.muskit.lock.FencingTokenContext;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard;
import org.springframework.stereotype.Service;

/**
 * 演示组合限流、幂等状态和 Redis 分布式锁保护订单提交。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class OrderSubmissionService {

    /**
     * 创建订单提交示例服务。
     */
    public OrderSubmissionService() {
    }

    /**
     * 提交指定订单，Redis 异常时保持失败语义而不降级。
     *
     * @param orderId 订单标识
     * @return 提交结果
     */
    @DistributedLock(
            name = "order-submit",
            key = "#orderId",
            waitTime = 500,
            leaseTime = 30_000,
            timeUnit = TimeUnit.MILLISECONDS,
            fencing = true)
    @Idempotent(operation = "order-submit", key = "#orderId")
    @RateLimitGuard(policy = "order-submit", key = "#orderId")
    public String submit(String orderId) {
        long fencingToken = FencingTokenContext.current().orElseThrow();
        return "submitted:" + orderId + ":token=" + fencingToken;
    }
}
