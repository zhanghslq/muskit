package io.github.zhanghslq.muskit.idempotency;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 以显式业务 ID 执行单条或批量幂等业务的程序化模板。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class IdempotencyTemplate {

    private final IdempotencyStore store;

    /**
     * 创建程序化幂等模板。
     *
     * @param store 幂等状态存储
     */
    public IdempotencyTemplate(IdempotencyStore store) {
        this.store = Objects.requireNonNull(store, "幂等状态存储不能为空");
    }

    /**
     * 按业务 ID 执行一次幂等业务。
     *
     * @param operation 稳定操作名称
     * @param businessId 业务唯一标识
     * @param processingTimeout 处理超时时间
     * @param retention 完成状态保留时间
     * @param action 业务动作
     * @param <T> 返回值类型
     * @return 业务返回值
     */
    public <T> T execute(
            String operation,
            String businessId,
            Duration processingTimeout,
            Duration retention,
            Supplier<T> action) {
        Objects.requireNonNull(action, "幂等业务动作不能为空");
        IdempotencyRequest request = new IdempotencyRequest(
                operation, businessId, processingTimeout, retention);
        IdempotencyClaim claim = acquire(request);
        Throwable businessFailure = null;
        try {
            return action.get();
        } catch (RuntimeException | Error failure) {
            businessFailure = failure;
            throw failure;
        } finally {
            finish(claim, businessFailure);
        }
    }

    /**
     * 逐项处理业务 ID 集合；已完成或正在处理的项目被分类跳过。
     *
     * <p>业务动作失败时立即停止批次并释放当前项，已成功项保持完成状态。</p>
     *
     * @param operation 稳定操作名称
     * @param businessIds 业务唯一标识集合
     * @param processingTimeout 处理超时时间
     * @param retention 完成状态保留时间
     * @param action 单项业务动作
     * @return 不包含业务 ID 的批量处理汇总
     */
    public IdempotencyBatchReport executeBatch(
            String operation,
            Collection<String> businessIds,
            Duration processingTimeout,
            Duration retention,
            Consumer<String> action) {
        Objects.requireNonNull(businessIds, "批量业务 ID 不能为空");
        Objects.requireNonNull(action, "批量幂等业务动作不能为空");
        int completed = 0;
        int duplicate = 0;
        int inProgress = 0;
        for (String businessId : businessIds) {
            IdempotencyRequest request = new IdempotencyRequest(
                    operation, businessId, processingTimeout, retention);
            IdempotencyAttempt attempt = store.tryStart(request);
            if (attempt.decision() == IdempotencyDecision.COMPLETED) {
                duplicate++;
                continue;
            }
            if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
                inProgress++;
                continue;
            }
            IdempotencyClaim claim = attempt.claim().orElseThrow(
                    () -> new IllegalStateException("幂等存储未返回所有权声明"));
            Throwable businessFailure = null;
            try {
                action.accept(businessId);
                completed++;
            } catch (RuntimeException | Error failure) {
                businessFailure = failure;
                throw failure;
            } finally {
                finish(claim, businessFailure);
            }
        }
        return new IdempotencyBatchReport(completed, duplicate, inProgress);
    }

    /**
     * 获取业务 ID 所有权并转换重复状态异常。
     *
     * @param request 幂等请求
     * @return 所有权声明
     */
    private IdempotencyClaim acquire(IdempotencyRequest request) {
        IdempotencyAttempt attempt = store.tryStart(request);
        if (attempt.decision() == IdempotencyDecision.COMPLETED) {
            throw new IdempotencyCompletedException(request.operation());
        }
        if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
            throw new IdempotencyInProgressException(request.operation());
        }
        return attempt.claim().orElseThrow(() -> new IllegalStateException("幂等存储未返回所有权声明"));
    }

    /**
     * 成功时提交状态，失败时释放所有权且不覆盖业务异常。
     *
     * @param claim 所有权声明
     * @param businessFailure 业务异常
     */
    private void finish(IdempotencyClaim claim, Throwable businessFailure) {
        try {
            if (businessFailure == null) {
                store.complete(claim);
            } else {
                store.release(claim);
            }
        } catch (RuntimeException stateFailure) {
            if (businessFailure == null) {
                throw stateFailure;
            }
            businessFailure.addSuppressed(stateFailure);
        }
    }
}
