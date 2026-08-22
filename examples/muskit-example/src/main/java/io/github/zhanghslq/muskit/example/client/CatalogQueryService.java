package io.github.zhanghslq.muskit.example.client;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.github.zhanghslq.muskit.resilience.deadline.Deadline;
import io.github.zhanghslq.muskit.resilience.deadline.DeadlineContext;
import io.github.zhanghslq.muskit.resilience.singleflight.SingleFlight;
import org.springframework.stereotype.Service;

/**
 * 演示使用 SingleFlight 合并查询并使用 Deadline 传递调用预算。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Service
public class CatalogQueryService {

    private final SingleFlight<String, String> singleFlight = new SingleFlight<>();

    /**
     * 创建目录查询示例服务。
     */
    public CatalogQueryService() {
    }

    /**
     * 合并同一商品当前正在执行的查询。
     *
     * @param productId 商品标识
     * @return 共享查询结果
     */
    public CompletionStage<String> query(String productId) {
        return singleFlight.execute(productId,
                () -> CompletableFuture.completedFuture("product:" + productId));
    }

    /**
     * 在指定 Deadline 预算内执行查询前检查。
     *
     * @param productId 商品标识
     * @param timeout 调用预算
     * @return 查询结果
     */
    public CompletionStage<String> queryWithin(String productId, Duration timeout) {
        try (DeadlineContext.Scope ignored = DeadlineContext.open(Deadline.after(timeout))) {
            DeadlineContext.check();
            return query(productId);
        }
    }
}
