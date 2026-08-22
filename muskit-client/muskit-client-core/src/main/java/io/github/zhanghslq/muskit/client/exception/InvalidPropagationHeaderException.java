package io.github.zhanghslq.muskit.client.exception;

/**
 * 表示收到不可信或格式错误的调用链传播请求头。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class InvalidPropagationHeaderException extends RuntimeException {

    /**
     * 创建传播请求头异常，不包含原始请求头值。
     *
     * @param reason 稳定失败原因
     */
    public InvalidPropagationHeaderException(String reason) {
        super(reason);
    }
}
