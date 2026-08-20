package io.github.zhanghslq.muskit.state;

/**
 * 状态迁移判定结果。
 *
 * @author zhs
 * @since 2026-08-20
 */
public enum StateTransitionStatus {

    /** 已匹配并允许迁移。 */
    APPLIED,

    /** 存在迁移规则，但业务守卫拒绝。 */
    GUARD_REJECTED,

    /** 当前状态和事件没有定义迁移规则。 */
    NO_TRANSITION
}
