package io.github.zhanghslq.muskit.observation.model;

/**
 * 允许进入公共指标的低基数标签键。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum MuskitTagKey {

    /** 能力或模块名称。 */
    COMPONENT("component"),

    /** 稳定策略名称。 */
    POLICY("policy"),

    /** 稳定业务操作名称。 */
    OPERATION("operation"),

    /** Provider 类型。 */
    PROVIDER("provider"),

    /** 执行结果。 */
    OUTCOME("outcome"),

    /** 有限状态名称。 */
    STATE("state"),

    /** 执行器配置名称。 */
    EXECUTOR("executor"),

    /** 缓存配置名称。 */
    CACHE("cache"),

    /** 有限实现类型。 */
    TYPE("type");

    private final String tagName;

    /**
     * 创建标签键。
     *
     * @param tagName Micrometer 标签名称
     */
    MuskitTagKey(String tagName) {
        this.tagName = tagName;
    }

    /**
     * 返回标签名称。
     *
     * @return 标签名称
     */
    public String tagName() {
        return tagName;
    }
}
