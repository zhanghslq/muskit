package io.github.zhanghslq.muskit.test.concurrency;

import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyScope;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式并发额度 Provider 的跨实例行为契约测试。
 *
 * @author zhs
 * @since 2026-08-20
 */
public abstract class DistributedConcurrencyLimiterContract extends ConcurrencyLimiterContract {

    /**
     * 创建分布式并发额度 Provider 契约测试基类。
     */
    protected DistributedConcurrencyLimiterContract() {
    }

    /**
     * 返回代表第一个应用实例的 Provider。
     *
     * @return 第一个 Provider
     */
    protected abstract ConcurrencyLimiter firstLimiter();

    /**
     * 返回代表第二个应用实例的 Provider。
     *
     * @return 第二个 Provider
     */
    protected abstract ConcurrencyLimiter secondLimiter();

    /**
     * 让基础契约使用第一个应用实例执行单 Provider 行为验证。
     *
     * @return 第一个 Provider
     */
    @Override
    protected final ConcurrencyLimiter limiter() {
        return firstLimiter();
    }

    /**
     * 验证多个应用实例共同遵守同一个并发上限。
     */
    @Test
    protected final void shouldCoordinateCapacityAcrossInstances() {
        ConcurrencyRequest request = request(ConcurrencyScope.GLOBAL, "", 1);
        ConcurrencyPermit first = acquire(firstLimiter(), request);

        assertFalse(acquireResult(secondLimiter(), request), "第二个实例不应穿透共享并发上限");
        first.close();
        assertTrue(acquireResult(secondLimiter(), request), "第一个实例释放后第二个实例应能获取额度");
    }
}
