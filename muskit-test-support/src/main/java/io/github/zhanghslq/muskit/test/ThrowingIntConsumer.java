package io.github.zhanghslq.muskit.test;

/**
 * 可抛出受检异常的整型参数消费函数，主要用于并发测试任务。
 *
 * @author zhs
 * @since 2026-08-20
 */
@FunctionalInterface
public interface ThrowingIntConsumer {

    /**
     * 执行指定序号的测试任务。
     *
     * @param index 任务序号
     * @throws Exception 任务执行异常
     */
    void accept(int index) throws Exception;
}

