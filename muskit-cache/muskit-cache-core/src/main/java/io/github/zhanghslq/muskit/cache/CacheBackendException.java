package io.github.zhanghslq.muskit.cache;

/**
 * 表示缓存后端操作失败。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class CacheBackendException extends RuntimeException {

    /**
     * 创建不包含业务缓存键的后端异常。
     *
     * @param operation 低基数操作名称
     * @param cause 后端异常
     */
    public CacheBackendException(String operation, Throwable cause) {
        super("缓存后端操作失败: operation=" + operation, cause);
    }
}
