package io.github.zhanghslq.muskit.audit.autoconfigure;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.zhanghslq.muskit.audit.AuditFailureListener;

/**
 * 对 BEST_EFFORT 审计丢弃产生明确告警的监听器。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class LoggingAuditFailureListener implements AuditFailureListener {

    private static final Logger LOGGER = Logger.getLogger(LoggingAuditFailureListener.class.getName());

    /**
     * 创建日志审计失败监听器。
     */
    public LoggingAuditFailureListener() {
    }

    /**
     * 记录不包含主体、操作者和扩展属性值的审计丢弃告警。
     *
     * @param action 稳定操作名称
     * @param failure 写入异常
     */
    @Override
    public void onFailure(String action, RuntimeException failure) {
        LOGGER.log(Level.WARNING, "Muskit 审计事件写入失败，BEST_EFFORT 模式已丢弃；action=" + action
                + ", failureType=" + failure.getClass().getSimpleName());
    }
}
