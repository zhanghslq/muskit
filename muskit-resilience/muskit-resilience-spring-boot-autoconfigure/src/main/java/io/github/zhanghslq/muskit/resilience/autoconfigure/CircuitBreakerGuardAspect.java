package io.github.zhanghslq.muskit.resilience.autoconfigure;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerGuard;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPermit;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicy;
import io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerPolicyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 解析 CircuitBreakerGuard 并记录同步或异步调用结果的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class CircuitBreakerGuardAspect implements Ordered {

    private final CircuitBreaker circuitBreaker;
    private final CircuitBreakerPolicyResolver policyResolver;
    private final int order;

    /**
     * 创建熔断注解切面。
     *
     * @param circuitBreaker 熔断 Provider
     * @param policyResolver 熔断策略解析器
     * @param order 切面顺序
     */
    public CircuitBreakerGuardAspect(
            CircuitBreaker circuitBreaker,
            CircuitBreakerPolicyResolver policyResolver,
            int order) {
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "熔断 Provider 不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "熔断策略解析器不能为空");
        this.order = order;
    }

    /**
     * 获取调用许可并在同步方法返回或异步任务真实完成时记录结果。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原业务结果或带结果记录回调的异步结果
     * @throws Throwable 原业务异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerGuard)"
            + " || @within(io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreakerGuard)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CircuitBreakerGuard guard = resolveGuard(method, invokedMethod, joinPoint.getTarget());
        CircuitBreakerPolicy policy = policyResolver.resolve(guard.policy());
        CircuitBreakerPermit permit = circuitBreaker.acquire(policy);
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> stage) {
                return stage.whenComplete((value, failure) -> {
                    if (failure == null) {
                        recordSuccess(permit, startedAt);
                    } else {
                        recordFailure(permit, startedAt, unwrap(failure));
                    }
                });
            }
            recordSuccess(permit, startedAt);
            return result;
        } catch (Throwable failure) {
            recordFailure(permit, startedAt, failure);
            throw failure;
        }
    }

    /**
     * 返回熔断切面顺序。
     *
     * @return 切面顺序
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * 记录成功并确保许可最终关闭。
     *
     * @param permit 调用许可
     * @param startedAt 调用开始单调时间
     */
    private void recordSuccess(CircuitBreakerPermit permit, long startedAt) {
        try {
            permit.success(elapsed(startedAt));
        } finally {
            permit.close();
        }
    }

    /**
     * 记录失败并确保许可最终关闭。
     *
     * @param permit 调用许可
     * @param startedAt 调用开始单调时间
     * @param failure 业务异常
     */
    private void recordFailure(CircuitBreakerPermit permit, long startedAt, Throwable failure) {
        try {
            permit.failure(elapsed(startedAt), failure);
        } finally {
            permit.close();
        }
    }

    /**
     * 计算非负调用耗时。
     *
     * @param startedAt 调用开始单调时间
     * @return 调用耗时
     */
    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }

    /**
     * 去除 CompletionStage 常见包装异常。
     *
     * @param failure 异步完成异常
     * @return 原始业务异常
     */
    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 解析代理调用对应的最具体方法。
     *
     * @param joinPoint 方法连接点
     * @return 最具体方法
     */
    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method interfaceMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return AopUtils.getMostSpecificMethod(interfaceMethod, AopUtils.getTargetClass(joinPoint.getTarget()));
    }

    /**
     * 按具体方法、代理签名和目标类型解析熔断注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名方法
     * @param target 目标对象
     * @return 熔断注解
     */
    private CircuitBreakerGuard resolveGuard(Method method, Method invokedMethod, Object target) {
        CircuitBreakerGuard guard = AnnotatedElementUtils.findMergedAnnotation(
                method, CircuitBreakerGuard.class);
        if (guard == null && method != invokedMethod) {
            guard = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, CircuitBreakerGuard.class);
        }
        if (guard == null) {
            guard = AnnotatedElementUtils.findMergedAnnotation(
                    AopUtils.getTargetClass(target), CircuitBreakerGuard.class);
        }
        if (guard == null) {
            throw new IllegalStateException("未找到熔断注解");
        }
        return guard;
    }
}
