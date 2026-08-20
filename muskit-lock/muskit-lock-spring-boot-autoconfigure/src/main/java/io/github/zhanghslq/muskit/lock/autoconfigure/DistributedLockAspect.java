package io.github.zhanghslq.muskit.lock.autoconfigure;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.zhanghslq.muskit.lock.DistributedLock;
import io.github.zhanghslq.muskit.lock.DistributedLockHandle;
import io.github.zhanghslq.muskit.lock.DistributedLockInterruptedException;
import io.github.zhanghslq.muskit.lock.DistributedLockProvider;
import io.github.zhanghslq.muskit.lock.DistributedLockRejectedException;
import io.github.zhanghslq.muskit.lock.DistributedLockRequest;
import io.github.zhanghslq.muskit.lock.FencingTokenContext;
import io.github.zhanghslq.muskit.lock.FencingTokenUnavailableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 解析 {@link DistributedLock} 并在方法执行期间持有锁的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class DistributedLockAspect implements Ordered {

    private final DistributedLockProvider lockProvider;
    private final LockObservation lockObservation;
    private final BeanFactory beanFactory;
    private final int order;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 创建分布式锁切面。
     *
     * @param lockProvider 锁提供器
     * @param lockObservation 锁指标记录器
     * @param beanFactory Spring Bean 工厂
     * @param order 切面顺序
     */
    public DistributedLockAspect(
            DistributedLockProvider lockProvider,
            LockObservation lockObservation,
            BeanFactory beanFactory,
            int order) {
        this.lockProvider = Objects.requireNonNull(lockProvider, "锁提供器不能为空");
        this.lockObservation = Objects.requireNonNull(lockObservation, "锁指标记录器不能为空");
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory 不能为空");
        this.order = order;
    }

    /**
     * 在目标方法执行前获取锁，并在同步执行结束或异步结果完成后释放锁。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原方法执行结果
     * @throws Throwable 原方法或切面执行异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.lock.DistributedLock)"
            + " || @within(io.github.zhanghslq.muskit.lock.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DistributedLock annotation = resolveAnnotation(method, invokedMethod, joinPoint.getTarget());
        String key = evaluateKey(annotation.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        DistributedLockHandle handle = acquire(toRequest(annotation, key));
        FencingTokenContext.Scope fencingScope = openFencingScope(annotation, handle);

        boolean closeSynchronously = true;
        Throwable invocationFailure = null;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> completionStage) {
                // 锁跟随异步结果的真实完成信号释放，不能在方法返回 CompletionStage 时提前释放。
                closeSynchronously = false;
                return completionStage.whenComplete((ignored, failure) -> close(handle, failure));
            }
            return result;
        } catch (Throwable throwable) {
            invocationFailure = throwable;
            throw throwable;
        } finally {
            try {
                if (fencingScope != null) {
                    fencingScope.close();
                }
            } finally {
                if (closeSynchronously) {
                    close(handle, invocationFailure);
                }
            }
        }
    }

    /**
     * 返回锁切面执行顺序，默认在并发控制和事务切面外层获取锁。
     *
     * @return 切面顺序
     */
    @Override
    public int getOrder() {
        return order;
    }

    /**
     * 解析代理调用对应的最具体方法。
     *
     * @param joinPoint 方法连接点
     * @return 最具体方法
     */
    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method interfaceMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        return AopUtils.getMostSpecificMethod(interfaceMethod, targetClass);
    }

    /**
     * 按具体方法、接口方法和目标类的顺序解析锁注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名上的方法
     * @param target 目标对象
     * @return 分布式锁注解
     */
    private DistributedLock resolveAnnotation(Method method, Method invokedMethod, Object target) {
        DistributedLock annotation = AnnotatedElementUtils.findMergedAnnotation(method, DistributedLock.class);
        if (annotation == null && method != invokedMethod) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, DistributedLock.class);
        }
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    AopUtils.getTargetClass(target), DistributedLock.class);
        }
        if (annotation == null) {
            throw new IllegalStateException("未找到分布式锁注解");
        }
        return annotation;
    }

    /**
     * 将注解属性转换为基础设施无关的锁请求。
     *
     * @param annotation 分布式锁注解
     * @param key 已计算的业务锁键
     * @return 锁请求
     */
    private DistributedLockRequest toRequest(DistributedLock annotation, String key) {
        if (annotation.waitTime() < 0) {
            throw new IllegalArgumentException("分布式锁 waitTime 不能为负数，lock=" + annotation.name());
        }
        if (annotation.leaseTime() != -1 && annotation.leaseTime() <= 0) {
            throw new IllegalArgumentException("分布式锁 leaseTime 必须为 -1 或正数，lock=" + annotation.name());
        }
        Duration waitTime = Duration.of(annotation.waitTime(), annotation.timeUnit().toChronoUnit());
        Duration leaseTime = annotation.leaseTime() == -1
                ? Duration.ZERO
                : Duration.of(annotation.leaseTime(), annotation.timeUnit().toChronoUnit());
        return new DistributedLockRequest(
                annotation.name(), key, waitTime, leaseTime, annotation.fair(),
                annotation.localFallback(), annotation.fencing());
    }

    /**
     * 在注解要求 fencing 时打开令牌作用域，Provider 语义不完整时先释放锁并失败。
     *
     * @param annotation 分布式锁注解
     * @param handle 已获取锁句柄
     * @return fencing token 作用域，未启用时为空
     */
    private FencingTokenContext.Scope openFencingScope(
            DistributedLock annotation,
            DistributedLockHandle handle) {
        if (!annotation.fencing()) {
            return null;
        }
        if (handle.fencingToken().isEmpty()) {
            handle.close();
            throw new FencingTokenUnavailableException(annotation.name());
        }
        return FencingTokenContext.open(handle.fencingToken().orElseThrow());
    }

    /**
     * 计算注解声明的业务锁键。
     *
     * @param keyExpression 业务锁键 SpEL 表达式
     * @param method 被调用方法
     * @param target 目标对象
     * @param arguments 方法参数
     * @return 业务锁键，未配置表达式时返回空字符串
     */
    private String evaluateKey(String keyExpression, Method method, Object target, Object[] arguments) {
        if (keyExpression == null || keyExpression.isBlank()) {
            return "";
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target,
                method,
                arguments,
                parameterNameDiscoverer);
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        Expression expression = expressionCache.computeIfAbsent(keyExpression, expressionParser::parseExpression);
        Object value = expression.getValue(context);
        return value == null ? "" : value.toString();
    }

    /**
     * 获取锁并将超时或中断转换为稳定的公共异常。
     *
     * @param request 锁请求
     * @return 已获取的锁句柄
     */
    private DistributedLockHandle acquire(DistributedLockRequest request) {
        LockObservation.Acquisition acquisition = lockObservation.start(request.name());
        try {
            Optional<DistributedLockHandle> handle = lockProvider.tryAcquire(request);
            if (handle.isEmpty()) {
                acquisition.complete(LockObservation.Outcome.REJECTED);
                throw new DistributedLockRejectedException(request.name());
            }
            acquisition.complete(LockObservation.Outcome.ACQUIRED);
            return handle.orElseThrow();
        } catch (InterruptedException exception) {
            acquisition.complete(LockObservation.Outcome.INTERRUPTED);
            Thread.currentThread().interrupt();
            throw new DistributedLockInterruptedException(request.name(), exception);
        } catch (DistributedLockRejectedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            acquisition.complete(LockObservation.Outcome.ERROR);
            throw exception;
        }
    }

    /**
     * 释放锁；业务调用已失败时将释放异常附加到原异常，避免覆盖业务失败原因。
     *
     * @param handle 锁句柄
     * @param existingFailure 已存在的业务或异步失败
     */
    private void close(DistributedLockHandle handle, Throwable existingFailure) {
        try {
            handle.close();
        } catch (RuntimeException releaseFailure) {
            if (existingFailure == null) {
                throw releaseFailure;
            }
            existingFailure.addSuppressed(releaseFailure);
        }
    }
}
