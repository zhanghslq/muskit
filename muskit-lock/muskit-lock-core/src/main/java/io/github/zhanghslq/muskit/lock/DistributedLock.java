package io.github.zhanghslq.muskit.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 声明方法或类型需要使用 Redis 分布式锁保护。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DistributedLock {

    /**
     * 返回低基数的锁名称，同一业务临界区应使用相同名称。
     *
     * @return 锁名称
     */
    String name();

    /**
     * 返回计算业务锁键的 SpEL 表达式，空字符串表示仅按锁名称互斥。
     *
     * @return 业务锁键表达式
     */
    String key() default "";

    /**
     * 返回获取锁的最长等待时间，零表示立即返回获取结果。
     *
     * @return 最长等待时间
     */
    long waitTime() default 0;

    /**
     * 返回锁租约时间，负一表示使用 Redisson 看门狗自动续期，正数表示固定租约。
     *
     * @return 锁租约时间
     */
    long leaseTime() default -1;

    /**
     * 返回等待时间和租约时间使用的时间单位。
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 返回是否使用 Redis 公平锁。
     *
     * @return 是否使用公平锁
     */
    boolean fair() default false;

    /**
     * 返回 Redis 获取锁异常时是否允许降级为仅当前 JVM 生效的本地锁。
     *
     * @return 是否允许本地锁降级
     */
    boolean localFallback() default false;

    /**
     * 返回是否要求 Redis 生成严格递增的 fencing token。
     *
     * <p>启用后 Redis 不可用时禁止降级为本地锁，业务可通过
     * {@link FencingTokenContext#current()} 获取令牌。</p>
     *
     * @return 是否启用 fencing token
     */
    boolean fencing() default false;
}
