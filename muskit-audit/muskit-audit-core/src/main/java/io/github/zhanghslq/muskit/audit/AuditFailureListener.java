package io.github.zhanghslq.muskit.audit;

/**
 * BEST_EFFORT 模式下接收审计丢弃通知的 SPI。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface AuditFailureListener {

    /**
     * 处理一次审计写入失败通知。
     *
     * @param action 稳定操作名称
     * @param failure 写入异常
     */
    void onFailure(String action, RuntimeException failure);
}
