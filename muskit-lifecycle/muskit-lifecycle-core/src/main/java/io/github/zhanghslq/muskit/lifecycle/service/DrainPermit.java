package io.github.zhanghslq.muskit.lifecycle.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 表示一个必须在工作真实结束后释放的在途工作许可。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainPermit implements AutoCloseable {

    private final Runnable releaseAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建在途工作许可。
     *
     * @param releaseAction 首次关闭时执行的释放动作
     */
    DrainPermit(Runnable releaseAction) {
        this.releaseAction = Objects.requireNonNull(releaseAction, "排空许可释放动作不能为空");
    }

    /**
     * 释放在途计数，重复关闭保持幂等。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaseAction.run();
        }
    }
}
