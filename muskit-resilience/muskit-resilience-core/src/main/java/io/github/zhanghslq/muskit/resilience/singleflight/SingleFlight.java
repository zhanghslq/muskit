package io.github.zhanghslq.muskit.resilience.singleflight;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 将同一业务键的并发调用合并为一次真实执行的 SingleFlight 工具。
 *
 * @param <K> 业务键类型
 * @param <V> 结果类型
 * @author zhs
 * @since 2026-08-20
 */
public final class SingleFlight<K, V> {

    private final ConcurrentMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * 创建空的 SingleFlight 实例。
     */
    public SingleFlight() {
    }

    /**
     * 执行或加入同一业务键当前正在执行的异步任务。
     *
     * @param key 业务键
     * @param action 仅由竞争胜出的调用执行一次的异步动作
     * @return 与共享结果联动但取消互不影响的调用方视图
     */
    public CompletionStage<V> execute(K key, Supplier<? extends CompletionStage<V>> action) {
        Objects.requireNonNull(key, "SingleFlight 业务键不能为空");
        Objects.requireNonNull(action, "SingleFlight 执行动作不能为空");
        CompletableFuture<V> promise = new CompletableFuture<>();
        CompletableFuture<V> existing = inFlight.putIfAbsent(key, promise);
        if (existing != null) {
            return callerView(existing);
        }

        try {
            CompletionStage<V> source = Objects.requireNonNull(action.get(), "SingleFlight 动作结果不能为空");
            source.whenComplete((value, failure) -> complete(key, promise, value, failure));
        } catch (Throwable failure) {
            complete(key, promise, null, failure);
        }
        return callerView(promise);
    }

    /**
     * 执行或加入同一业务键当前正在执行的同步任务。
     *
     * @param key 业务键
     * @param action 仅执行一次的同步动作
     * @return 共享业务结果
     * @throws Exception 同步动作异常
     */
    public V executeSync(K key, Callable<V> action) throws Exception {
        Objects.requireNonNull(action, "SingleFlight 同步动作不能为空");
        try {
            return execute(key, () -> {
                try {
                    return CompletableFuture.completedFuture(action.call());
                } catch (Throwable failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }).toCompletableFuture().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    /**
     * 返回当前正在执行的不同业务键数量。
     *
     * @return 进行中任务数量
     */
    public int inFlightCount() {
        return inFlight.size();
    }

    /**
     * 完成共享结果并仅移除当前占位对象，避免旧任务删除后续同键新任务。
     *
     * @param key 业务键
     * @param promise 当前共享结果
     * @param value 成功值
     * @param failure 失败原因
     */
    private void complete(K key, CompletableFuture<V> promise, V value, Throwable failure) {
        try {
            if (failure == null) {
                promise.complete(value);
            } else {
                promise.completeExceptionally(failure);
            }
        } finally {
            inFlight.remove(key, promise);
        }
    }

    /**
     * 创建独立调用方视图，避免调用方取消共享占位结果。
     *
     * @param shared 共享结果
     * @return 调用方结果视图
     */
    private CompletionStage<V> callerView(CompletableFuture<V> shared) {
        return shared.thenApply(Function.identity());
    }
}
