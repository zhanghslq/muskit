package io.github.zhanghslq.muskit.audit.autoconfigure.aspect;

import io.github.zhanghslq.muskit.audit.annotation.Audited;
import io.github.zhanghslq.muskit.audit.model.AuditOutcome;
import io.github.zhanghslq.muskit.audit.service.AuditRecorder;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 在同步或异步业务实际完成后记录注解审计结果的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class AuditOperationAspect {

    private final AuditRecorder recorder;

    /**
     * 创建审计操作切面。
     *
     * @param recorder 审计记录器
     */
    public AuditOperationAspect(AuditRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "审计记录器不能为空");
    }

    /**
     * 执行业务方法，并按其真实完成结果记录审计事件。
     *
     * @param joinPoint 业务连接点
     * @param audited 审计注解
     * @return 原业务返回值或带审计回调的异步返回值
     * @throws Throwable 业务或强约束审计异常
     */
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable failure) {
            record(audited, AuditOutcome.FAILURE);
            throw failure;
        }
        if (result instanceof CompletionStage<?> stage) {
            // 异步方法必须等真实任务完成后再记审计，不能把“成功提交任务”误记为业务成功。
            return stage.whenComplete((value, failure) -> record(
                    audited,
                    failure == null ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE));
        }
        record(audited, AuditOutcome.SUCCESS);
        return result;
    }

    /**
     * 记录不自动采集方法参数的安全注解审计事件。
     *
     * @param audited 审计注解
     * @param outcome 业务结果
     */
    private void record(Audited audited, AuditOutcome outcome) {
        recorder.record(
                audited.action(),
                outcome,
                audited.subjectType(),
                null,
                outcome == AuditOutcome.FAILURE ? "business_error" : null,
                Map.of());
    }
}
