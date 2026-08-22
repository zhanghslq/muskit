package io.github.zhanghslq.muskit.resilience.autoconfigure.retry;

import io.github.zhanghslq.muskit.resilience.retry.RetryExecutor;
import io.github.zhanghslq.muskit.resilience.retry.RetryGuard;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicy;
import io.github.zhanghslq.muskit.resilience.retry.RetryPolicyResolver;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 解析 RetryGuard 并执行同步或 CompletionStage 重试的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class RetryGuardAspect implements Ordered {

    private final RetryExecutor retryExecutor;
    private final RetryPolicyResolver policyResolver;
    private final int order;

    /**
     * 创建重试注解切面。
     *
     * @param retryExecutor 重试执行器
     * @param policyResolver 重试策略解析器
     * @param order 切面顺序
     */
    public RetryGuardAspect(
            RetryExecutor retryExecutor,
            RetryPolicyResolver policyResolver,
            int order) {
        this.retryExecutor = Objects.requireNonNull(retryExecutor, "重试执行器不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "重试策略解析器不能为空");
        this.order = order;
    }

    /**
     * 按注解策略重试同步或 CompletionStage 方法。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 最终业务结果
     * @throws Throwable 最终业务异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.resilience.retry.RetryGuard)"
            + " || @within(io.github.zhanghslq.muskit.resilience.retry.RetryGuard)")
    public Object retry(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RetryGuard guard = resolveGuard(method, invokedMethod, joinPoint.getTarget());
        RetryPolicy policy = policyResolver.resolve(guard.policy());
        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            return retryExecutor.executeAsync(policy, () -> invokeAsync(joinPoint));
        }
        return retryExecutor.execute(policy, joinPoint::proceed);
    }

    /**
     * 返回重试切面顺序。
     *
     * @return 切面顺序
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * 调用一次异步业务方法并校验真实返回类型。
     *
     * @param joinPoint 方法连接点
     * @return 异步结果
     * @throws Throwable 业务调用异常
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<Object> invokeAsync(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (!(result instanceof CompletionStage<?> stage)) {
            throw new IllegalStateException("声明 CompletionStage 的重试方法返回了其他类型");
        }
        return (CompletionStage<Object>) stage;
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
     * 按具体方法、代理签名和目标类型解析重试注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名方法
     * @param target 目标对象
     * @return 重试注解
     */
    private RetryGuard resolveGuard(Method method, Method invokedMethod, Object target) {
        RetryGuard guard = AnnotatedElementUtils.findMergedAnnotation(method, RetryGuard.class);
        if (guard == null && method != invokedMethod) {
            guard = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, RetryGuard.class);
        }
        if (guard == null) {
            guard = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(target), RetryGuard.class);
        }
        if (guard == null) {
            throw new IllegalStateException("未找到重试注解");
        }
        return guard;
    }
}
