package io.github.zhanghslq.muskit.lifecycle.model;

import java.util.List;

/**
 * 一次全局排空等待的汇总结果。
 *
 * @param completed 是否全部排空
 * @param incompleteComponents 超时后仍未排空的低基数组件名称
 * @author zhs
 * @since 2026-08-20
 */
public record DrainReport(boolean completed, List<String> incompleteComponents) {

    /**
     * 创建不可变排空报告。
     */
    public DrainReport {
        incompleteComponents = List.copyOf(incompleteComponents);
        if (completed && !incompleteComponents.isEmpty()) {
            throw new IllegalArgumentException("完成的排空报告不能包含未完成组件");
        }
    }
}
