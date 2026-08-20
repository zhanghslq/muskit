package io.github.zhanghslq.muskit.cache;

/**
 * 缓存后端不可用时由业务显式选择的失败语义。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum CacheFailureMode {

    /** 后端失败立即终止业务调用。 */
    FAIL_FAST,

    /** 明确允许绕过缓存调用数据加载器，但不会把失效操作静默降级。 */
    LOAD_WITHOUT_CACHE
}
