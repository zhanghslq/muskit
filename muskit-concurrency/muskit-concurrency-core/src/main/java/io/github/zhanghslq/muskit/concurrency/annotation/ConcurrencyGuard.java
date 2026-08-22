package io.github.zhanghslq.muskit.concurrency.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法或类需要受到指定并发策略保护。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ConcurrencyGuard {

    /**
     * 返回配置中定义的并发策略名称。
     *
     * @return 并发策略名称
     */
    String policy();

    /**
     * 返回用于 KEY 范围隔离的 SpEL 表达式。
     *
     * @return 业务键表达式
     */
    String key() default "";
}

