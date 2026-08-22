package io.github.zhanghslq.muskit.concurrency.spi;

/**
 * 已成功获取的并发额度，关闭时释放额度。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface ConcurrencyPermit extends AutoCloseable {

    /**
     * 释放并发额度，重复调用应当保持幂等。
     */
    @Override
    void close();
}

