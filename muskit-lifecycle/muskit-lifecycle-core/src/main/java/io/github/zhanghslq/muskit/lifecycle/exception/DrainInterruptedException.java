package io.github.zhanghslq.muskit.lifecycle.exception;

/**
 * 表示等待组件排空时当前线程被中断。
 *
 * @author zhs
 * @since 2026-08-20
 */
public final class DrainInterruptedException extends RuntimeException {

    /**
     * 创建排空中断异常。
     *
     * @param componentName 低基数组件名称
     * @param cause 中断异常
     */
    public DrainInterruptedException(String componentName, InterruptedException cause) {
        super("等待组件排空时被中断: " + componentName, cause);
    }
}
