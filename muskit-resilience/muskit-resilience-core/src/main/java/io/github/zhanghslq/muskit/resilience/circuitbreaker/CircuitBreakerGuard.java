package io.github.zhanghslq.muskit.resilience.circuitbreaker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法或类型使用指定熔断策略。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface CircuitBreakerGuard {

    /**
     * 返回稳定的低基数熔断策略名称。
     *
     * @return 策略名称
     */
    String policy();
}
