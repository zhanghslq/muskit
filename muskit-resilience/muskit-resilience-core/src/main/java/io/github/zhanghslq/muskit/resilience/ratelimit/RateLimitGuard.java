package io.github.zhanghslq.muskit.resilience.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法或类型需要受到指定限流策略保护。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RateLimitGuard {

    /**
     * 返回配置中的限流策略名称。
     *
     * @return 限流策略名称
     */
    String policy();

    /**
     * 返回按业务键隔离时使用的 SpEL 表达式。
     *
     * @return 业务键表达式
     */
    String key() default "";
}
