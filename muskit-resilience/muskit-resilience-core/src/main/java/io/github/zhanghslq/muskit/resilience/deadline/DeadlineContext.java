package io.github.zhanghslq.muskit.resilience.deadline;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 管理当前线程 Deadline 的嵌套作用域和显式跨线程包装。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DeadlineContext {

    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    /**
     * 禁止创建静态工具类实例。
     */
    private DeadlineContext() {
    }

    /**
     * 返回当前线程 Deadline。
     *
     * @return 当前 Deadline，不存在时为空
     */
    public static Optional<Deadline> current() {
        Frame frame = CURRENT.get();
        return frame == null ? Optional.empty() : Optional.of(frame.deadline);
    }

    /**
     * 打开 Deadline 作用域；存在父 Deadline 时自动选择更早者。
     *
     * @param requested 请求的 Deadline
     * @return 必须关闭的作用域
     */
    public static Scope open(Deadline requested) {
        Objects.requireNonNull(requested, "Deadline 不能为空");
        Frame previous = CURRENT.get();
        Deadline effective = previous == null ? requested : previous.deadline.earliest(requested);
        Frame current = new Frame(effective);
        CURRENT.set(current);
        return new Scope(previous, current, Thread.currentThread());
    }

    /**
     * 检查当前 Deadline，到期时抛出异常。
     */
    public static void check() {
        Frame current = CURRENT.get();
        if (current != null) {
            current.deadline.throwIfExpired();
        }
    }

    /**
     * 捕获当前 Deadline 并包装 Runnable，执行后恢复目标线程原状态。
     *
     * @param task 原任务
     * @return 带 Deadline 传播的任务
     */
    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "Deadline 包装任务不能为空");
        Frame current = CURRENT.get();
        if (current == null) {
            return task;
        }
        Deadline captured = current.deadline;
        return () -> {
            try (Scope ignored = open(captured)) {
                task.run();
            }
        };
    }

    /**
     * 捕获当前 Deadline 并包装 Callable，执行后恢复目标线程原状态。
     *
     * @param task 原任务
     * @param <V> 任务结果类型
     * @return 带 Deadline 传播的任务
     */
    public static <V> Callable<V> wrap(Callable<V> task) {
        Objects.requireNonNull(task, "Deadline 包装任务不能为空");
        Frame current = CURRENT.get();
        if (current == null) {
            return task;
        }
        Deadline captured = current.deadline;
        return () -> {
            try (Scope ignored = open(captured)) {
                return task.call();
            }
        };
    }

    /**
     * Deadline 线程作用域，必须按嵌套顺序在创建线程关闭。
     *
     * @author zhs
     * @since 2026-08-20
     */
    public static final class Scope implements AutoCloseable {

        private final Frame previous;
        private final Frame current;
        private final Thread ownerThread;
        private boolean closed;

        /**
         * 创建 Deadline 线程作用域。
         *
         * @param previous 上一层 Deadline 帧
         * @param current 当前 Deadline 帧
         * @param ownerThread 创建作用域的线程
         */
        private Scope(Frame previous, Frame current, Thread ownerThread) {
            this.previous = previous;
            this.current = current;
            this.ownerThread = ownerThread;
        }

        /**
         * 恢复上一层 Deadline，重复关闭保持幂等。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("Deadline 作用域必须在创建线程关闭");
            }
            if (CURRENT.get() != current) {
                throw new IllegalStateException("Deadline 作用域必须按嵌套顺序关闭");
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * 区分每一层嵌套作用域的内部 Deadline 帧。
     *
     * @author zhs
     * @since 2026-08-20
     */
    private static final class Frame {

        private final Deadline deadline;

        /**
         * 创建 Deadline 帧。
         *
         * @param deadline 当前层有效 Deadline
         */
        private Frame(Deadline deadline) {
            this.deadline = deadline;
        }
    }
}
