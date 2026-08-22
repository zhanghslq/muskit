package io.github.zhanghslq.muskit.audit.service;

import io.github.zhanghslq.muskit.audit.exception.AuditWriteException;
import io.github.zhanghslq.muskit.audit.model.AuditEvent;
import io.github.zhanghslq.muskit.audit.model.AuditFailureMode;
import io.github.zhanghslq.muskit.audit.model.AuditOutcome;
import io.github.zhanghslq.muskit.audit.spi.AuditFailureListener;
import io.github.zhanghslq.muskit.audit.spi.AuditPrincipalProvider;
import io.github.zhanghslq.muskit.audit.spi.AuditWriter;
import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 创建、写入并观测审计事件的业务入口。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class AuditRecorder {

    private final AuditWriter writer;
    private final AuditPrincipalProvider principalProvider;
    private final AuditFailureListener failureListener;
    private final AuditFailureMode failureMode;
    private final MuskitObservationRegistry observationRegistry;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    /**
     * 使用完整依赖创建审计记录器。
     *
     * @param writer 审计 Writer
     * @param principalProvider 操作者 Provider
     * @param failureListener 失败通知监听器
     * @param failureMode 写入失败模式
     * @param observationRegistry 统一观测注册器
     * @param clock 时间来源
     * @param idSupplier 事件标识生成器
     */
    public AuditRecorder(
            AuditWriter writer,
            AuditPrincipalProvider principalProvider,
            AuditFailureListener failureListener,
            AuditFailureMode failureMode,
            MuskitObservationRegistry observationRegistry,
            Clock clock,
            Supplier<String> idSupplier) {
        this.writer = Objects.requireNonNull(writer, "审计 Writer 不能为空");
        this.principalProvider = Objects.requireNonNull(principalProvider, "审计操作者 Provider 不能为空");
        this.failureListener = Objects.requireNonNull(failureListener, "审计失败监听器不能为空");
        this.failureMode = Objects.requireNonNull(failureMode, "审计失败模式不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        this.clock = Objects.requireNonNull(clock, "审计时间来源不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "审计事件标识生成器不能为空");
    }

    /**
     * 使用系统时间和 UUID 创建审计记录器。
     *
     * @param writer 审计 Writer
     * @param principalProvider 操作者 Provider
     * @param failureListener 失败通知监听器
     * @param failureMode 写入失败模式
     * @param observationRegistry 统一观测注册器
     */
    public AuditRecorder(
            AuditWriter writer,
            AuditPrincipalProvider principalProvider,
            AuditFailureListener failureListener,
            AuditFailureMode failureMode,
            MuskitObservationRegistry observationRegistry) {
        this(writer, principalProvider, failureListener, failureMode, observationRegistry,
                Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    /**
     * 记录最小审计事件。
     *
     * @param action 稳定操作名称
     * @param outcome 业务结果
     */
    public void record(String action, AuditOutcome outcome) {
        record(action, outcome, null, null, null, Map.of());
    }

    /**
     * 记录完整审计事件。
     *
     * @param action 稳定操作名称
     * @param outcome 业务结果
     * @param subjectType 主体类型，可为空
     * @param subjectId 主体标识，可为空
     * @param errorCode 稳定错误码，可为空
     * @param attributes 扩展属性
     */
    public void record(
            String action,
            AuditOutcome outcome,
            String subjectType,
            String subjectId,
            String errorCode,
            Map<String, String> attributes) {
        AuditEvent event = new AuditEvent(
                idSupplier.get(),
                clock.instant(),
                action,
                outcome,
                principalProvider.currentPrincipal().orElse(null),
                subjectType,
                subjectId,
                errorCode,
                attributes);
        try {
            writer.write(event);
            observe(action, outcome.name().toLowerCase());
        } catch (RuntimeException exception) {
            observe(action, "dropped");
            if (failureMode == AuditFailureMode.FAIL_FAST) {
                throw exception instanceof AuditWriteException ? exception : new AuditWriteException(exception);
            }
            // BEST_EFFORT 只在显式配置时生效，同时通过监听器产生可见告警。
            failureListener.onFailure(action, exception);
        }
    }

    /**
     * 记录审计写入结果指标。
     *
     * @param action 稳定操作名称
     * @param outcome 写入结果
     */
    private void observe(String action, String outcome) {
        observationRegistry.increment(
                MuskitMetric.AUDIT_WRITE,
                ObservationTags.of(MuskitTagKey.OPERATION, action)
                        .and(MuskitTagKey.OUTCOME, outcome));
    }
}
