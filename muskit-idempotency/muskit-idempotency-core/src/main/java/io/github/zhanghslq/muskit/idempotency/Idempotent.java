package io.github.zhanghslq.muskit.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 声明方法或类型需要按业务键执行幂等状态控制。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Idempotent {

    /**
     * 返回低基数的幂等操作名称。
     *
     * @return 幂等操作名称
     */
    String operation();

    /**
     * 返回计算业务幂等键的 SpEL 表达式。
     *
     * @return 业务幂等键表达式
     */
    String key();

    /**
     * 返回处理中状态失效前的时间长度。
     *
     * @return 处理超时时间
     */
    long processingTimeout() default 30;

    /**
     * 返回处理超时时间单位。
     *
     * @return 处理超时时间单位
     */
    TimeUnit processingTimeoutUnit() default TimeUnit.SECONDS;

    /**
     * 返回成功状态保留的时间长度。
     *
     * @return 成功状态保留时间
     */
    long retention() default 24;

    /**
     * 返回成功状态保留时间单位。
     *
     * @return 成功状态保留时间单位
     */
    TimeUnit retentionUnit() default TimeUnit.HOURS;

    /**
     * 返回全局启用 lease 续期时当前操作是否参与自动续期。
     *
     * @return 是否参与自动续期
     */
    boolean autoRenew() default true;
}
