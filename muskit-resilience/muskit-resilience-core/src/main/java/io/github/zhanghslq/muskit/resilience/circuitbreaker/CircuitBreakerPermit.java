package io.github.zhanghslq.muskit.resilience.circuitbreaker;

import java.time.Duration;

/**
 * 一次熔断调用许可，结果记录和关闭必须幂等。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface CircuitBreakerPermit extends AutoCloseable {

    /**
     * 记录成功调用及耗时。
     *
     * @param duration 调用耗时
     */
    void success(Duration duration);

    /**
     * 记录失败调用及耗时。
     *
     * @param duration 调用耗时
     * @param failure 业务异常
     */
    void failure(Duration duration, Throwable failure);

    /**
     * 未记录结果时释放许可，已经记录或关闭时保持幂等。
     */
    @Override
    void close();
}
