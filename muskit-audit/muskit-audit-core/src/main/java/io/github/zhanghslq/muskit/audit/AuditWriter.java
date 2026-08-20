package io.github.zhanghslq.muskit.audit;

/**
 * 审计事件持久化 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface AuditWriter {

    /**
     * 持久化一条审计事件。
     *
     * @param event 审计事件
     */
    void write(AuditEvent event);
}
