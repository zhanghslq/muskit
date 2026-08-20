package io.github.zhanghslq.muskit.cache;

/**
 * 表示缓存值编码或解码失败。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class CacheCodecException extends RuntimeException {

    /**
     * 创建不包含业务值和缓存键的编解码异常。
     *
     * @param operation 编码或解码操作名称
     * @param cause 原异常
     */
    public CacheCodecException(String operation, Throwable cause) {
        super("缓存值编解码失败: operation=" + operation, cause);
    }
}
