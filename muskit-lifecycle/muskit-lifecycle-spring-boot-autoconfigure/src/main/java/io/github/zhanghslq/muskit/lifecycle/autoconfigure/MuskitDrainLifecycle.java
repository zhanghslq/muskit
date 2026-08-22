package io.github.zhanghslq.muskit.lifecycle.autoconfigure;

import io.github.zhanghslq.muskit.lifecycle.model.DrainReport;
import io.github.zhanghslq.muskit.lifecycle.service.DrainController;
import io.github.zhanghslq.muskit.lifecycle.service.DrainCoordinator;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * 在 Spring 停止阶段先发布拒绝流量状态，再异步等待全部组件排空。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class MuskitDrainLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuskitDrainLifecycle.class);

    private final DrainCoordinator coordinator;
    private final DrainController requestController;
    private final ApplicationContext applicationContext;
    private final Duration shutdownTimeout;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * 创建 Spring 排空生命周期。
     *
     * @param coordinator 全局排空协调器
     * @param requestController HTTP 请求控制器
     * @param applicationContext Spring 应用上下文
     * @param shutdownTimeout 全局排空超时
     */
    public MuskitDrainLifecycle(
            DrainCoordinator coordinator,
            DrainController requestController,
            ApplicationContext applicationContext,
            Duration shutdownTimeout) {
        this.coordinator = Objects.requireNonNull(coordinator, "排空协调器不能为空");
        this.requestController = Objects.requireNonNull(requestController, "HTTP 排空控制器不能为空");
        this.applicationContext = Objects.requireNonNull(applicationContext, "应用上下文不能为空");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "排空超时不能为空");
    }

    /**
     * 恢复接流并发布 Spring Boot 接受流量状态。
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            requestController.startAccepting();
            AvailabilityChangeEvent.publish(applicationContext, this, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    /**
     * 同步执行摘流和排空，主要用于直接生命周期调用。
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            drain();
        }
    }

    /**
     * 在虚拟线程中等待排空，完成后通知 Spring 继续关闭流程。
     *
     * @param callback 排空结束回调
     */
    @Override
    public void stop(Runnable callback) {
        Objects.requireNonNull(callback, "生命周期停止回调不能为空");
        if (!running.compareAndSet(true, false)) {
            callback.run();
            return;
        }
        Thread.ofVirtual().name("muskit-lifecycle-drain").start(() -> {
            try {
                drain();
            } finally {
                callback.run();
            }
        });
    }

    /**
     * 返回生命周期当前是否运行。
     *
     * @return 是否运行
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 返回最高生命周期阶段，使本组件在关闭时优先摘流。
     *
     * @return 生命周期阶段
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 发布拒绝流量状态并使用共享预算等待所有组件排空。
     */
    private void drain() {
        AvailabilityChangeEvent.publish(applicationContext, this, ReadinessState.REFUSING_TRAFFIC);
        DrainReport report = coordinator.drain(shutdownTimeout);
        if (!report.completed()) {
            LOGGER.warn("Muskit 排空等待超时，未完成组件={}", report.incompleteComponents());
        }
    }
}
