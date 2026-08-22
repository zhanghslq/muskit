package io.github.zhanghslq.muskit.lifecycle.spi;

import java.time.Duration;

/**
 * 定义可拒绝新工作并等待存量工作完成的组件边界。
 *
 * @author zhs
 * @since 2026-08-20
 */
public interface Drainable {

    /**
     * 返回用于日志和状态汇总的低基数组件名称。
     *
     * @return 组件名称
     */
    String name();

    /**
     * 进入排空状态，重复调用保持幂等。
     */
    void beginDrain();

    /**
     * 等待在途工作完成，调用时会确保组件已经开始排空。
     *
     * @param timeout 最大等待时间
     * @return 是否在期限内完成排空
     */
    boolean awaitDrained(Duration timeout);
}
