package io.github.zhanghslq.muskit.concurrency.spi;

import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import java.util.Optional;

/**
 * 并发额度提供器 SPI，可由本地信号量或分布式存储实现。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface ConcurrencyLimiter {

    /**
     * 尝试在策略规定的等待时间内获取并发额度。
     *
     * @param request 并发额度请求
     * @return 获取成功时返回并发额度，否则返回空
     * @throws InterruptedException 等待并发额度期间线程被中断
     */
    Optional<ConcurrencyPermit> tryAcquire(ConcurrencyRequest request) throws InterruptedException;
}

