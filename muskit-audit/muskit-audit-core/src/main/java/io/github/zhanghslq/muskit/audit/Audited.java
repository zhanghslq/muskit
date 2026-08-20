package io.github.zhanghslq.muskit.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明需要记录成功或失败结果的审计操作。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * 返回稳定的低基数操作名称。
     *
     * @return 操作名称
     */
    String action();

    /**
     * 返回可选主体类型，注解模式不自动采集方法参数。
     *
     * @return 主体类型
     */
    String subjectType() default "";
}
