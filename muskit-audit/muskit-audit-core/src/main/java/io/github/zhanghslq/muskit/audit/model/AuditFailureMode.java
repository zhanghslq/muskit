package io.github.zhanghslq.muskit.audit.model;

/**
 * 审计写入失败时的显式处理模式。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum AuditFailureMode {

    /** 审计失败直接使当前调用失败，适合合规强约束场景。 */
    FAIL_FAST,

    /** 业务调用继续，但必须通过监听器和指标暴露审计丢弃。 */
    BEST_EFFORT
}
