package io.github.zhanghslq.muskit.outbox.autoconfigure.scheduling;

import io.github.zhanghslq.muskit.outbox.model.OutboxDispatchReport;
import io.github.zhanghslq.muskit.outbox.service.OutboxDispatchService;
import io.github.zhanghslq.muskit.outbox.spi.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 随 Spring 容器生命周期启动和停止的 Outbox 后台轮询器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class OutboxPollingLifecycle implements SmartLifecycle {

    private static final Log LOGGER = LogFactory.getLog(OutboxPollingLifecycle.class);

    private final OutboxDispatchService dispatchService;
    private final OutboxRepository repository;
    private final Duration pollInterval;
    private final Duration publishedRetention;
    private final Clock clock;
    private final AtomicLong cycle = new AtomicLong();

    private volatile boolean running;
    private ScheduledExecutorService scheduler;

    /**
     * 使用 UTC 系统时钟创建后台轮询器。
     *
     * @param dispatchService Outbox 批量发布服务
     * @param repository Outbox 存储
     * @param pollInterval 轮询间隔
     * @param publishedRetention 已发布事件保留时间
     */
    public OutboxPollingLifecycle(
            OutboxDispatchService dispatchService,
            OutboxRepository repository,
            Duration pollInterval,
            Duration publishedRetention) {
        this(dispatchService, repository, pollInterval, publishedRetention, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建后台轮询器。
     *
     * @param dispatchService Outbox 批量发布服务
     * @param repository Outbox 存储
     * @param pollInterval 轮询间隔
     * @param publishedRetention 已发布事件保留时间
     * @param clock 清理截止时间时钟
     */
    public OutboxPollingLifecycle(
            OutboxDispatchService dispatchService,
            OutboxRepository repository,
            Duration pollInterval,
            Duration publishedRetention,
            Clock clock) {
        this.dispatchService = Objects.requireNonNull(dispatchService, "Outbox 发布服务不能为空");
        this.repository = Objects.requireNonNull(repository, "Outbox 存储不能为空");
        this.pollInterval = positive(pollInterval, "Outbox 轮询间隔必须大于 0");
        this.publishedRetention = positive(publishedRetention, "Outbox 已发布事件保留时间必须大于 0");
        this.clock = Objects.requireNonNull(clock, "Outbox 清理时钟不能为空");
    }

    /**
     * 启动单线程守护轮询器，重复启动保持幂等。
     */
    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "muskit-outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        scheduler.scheduleWithFixedDelay(
                this::runCycle,
                0,
                pollInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 停止轮询器并中断正在等待的发布，重复停止保持幂等。
     */
    @Override
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * 返回轮询器是否已经启动。
     *
     * @return 是否运行中
     */
    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 执行一次发布，并按低频周期清理已发布历史记录。
     */
    private void runCycle() {
        try {
            OutboxDispatchReport report = dispatchService.dispatchBatch();
            if (report.claimed() > 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("Outbox batch finished: claimed=" + report.claimed()
                        + ", published=" + report.published() + ", failed=" + report.failed());
            }
            if ((cycle.incrementAndGet() & 255L) == 0L) {
                repository.deletePublishedBefore(clock.instant().minus(publishedRetention));
            }
        } catch (RuntimeException exception) {
            // 日志只记录异常类型，避免底层异常消息意外携带事件标识或消息内容。
            LOGGER.error("Outbox polling cycle failed: " + exception.getClass().getSimpleName());
        }
    }

    /**
     * 校验正数时间配置。
     *
     * @param duration 时间长度
     * @param message 校验消息
     * @return 原时间长度
     */
    private Duration positive(Duration duration, String message) {
        Objects.requireNonNull(duration, message);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return duration;
    }
}
