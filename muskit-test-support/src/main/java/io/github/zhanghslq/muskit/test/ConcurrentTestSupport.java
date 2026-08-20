package io.github.zhanghslq.muskit.test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于虚拟线程的并发测试辅助工具。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class ConcurrentTestSupport {

    /**
     * 工具类不允许实例化。
     */
    private ConcurrentTestSupport() {
    }

    /**
     * 同时启动指定数量的虚拟线程并等待全部任务执行完成。
     *
     * @param taskCount 任务数量
     * @param timeout 全部任务的最大执行时间
     * @param task 测试任务
     */
    public static void runConcurrently(int taskCount, Duration timeout, ThrowingIntConsumer task) {
        if (taskCount <= 0) {
            throw new IllegalArgumentException("任务数量必须大于 0");
        }
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(taskCount);
            for (int index = 0; index < taskCount; index++) {
                int taskIndex = index;
                futures.add(executor.submit(() -> {
                    executeTask(taskIndex, ready, start, task);
                    return null;
                }));
            }
            awaitReady(ready, timeout);
            start.countDown();
            awaitFutures(futures, timeout);
        }
    }

    /**
     * 执行单个并发测试任务。
     *
     * @param index 任务序号
     * @param ready 就绪信号
     * @param start 开始信号
     * @param task 测试任务
     * @throws Exception 任务执行异常
     */
    private static void executeTask(
            int index,
            CountDownLatch ready,
            CountDownLatch start,
            ThrowingIntConsumer task) throws Exception {
        ready.countDown();
        start.await();
        task.accept(index);
    }

    /**
     * 等待所有测试任务进入就绪状态。
     *
     * @param ready 就绪信号
     * @param timeout 最大等待时间
     */
    private static void awaitReady(CountDownLatch ready, Duration timeout) {
        try {
            if (!ready.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("并发测试任务未在限定时间内就绪");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发测试任务就绪时被中断", exception);
        }
    }

    /**
     * 等待所有测试任务执行完成并传播任务异常。
     *
     * @param futures 测试任务结果
     * @param timeout 最大等待时间
     */
    private static void awaitFutures(List<Future<?>> futures, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Future<?> future : futures) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("并发测试任务执行超时");
            }
            try {
                future.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待并发测试任务完成时被中断", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("并发测试任务执行失败", exception.getCause());
            } catch (TimeoutException exception) {
                throw new IllegalStateException("并发测试任务执行超时", exception);
            }
        }
    }
}
