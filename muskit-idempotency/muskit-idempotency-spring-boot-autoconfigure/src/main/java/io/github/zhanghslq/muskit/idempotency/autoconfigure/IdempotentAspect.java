package io.github.zhanghslq.muskit.idempotency.autoconfigure;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.zhanghslq.muskit.idempotency.IdempotencyAttempt;
import io.github.zhanghslq.muskit.idempotency.IdempotencyClaim;
import io.github.zhanghslq.muskit.idempotency.IdempotencyCompletedException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyDecision;
import io.github.zhanghslq.muskit.idempotency.IdempotencyInProgressException;
import io.github.zhanghslq.muskit.idempotency.IdempotencyRequest;
import io.github.zhanghslq.muskit.idempotency.IdempotencyStore;
import io.github.zhanghslq.muskit.idempotency.Idempotent;
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
 * 解析 {@link Idempotent} 并驱动幂等状态机的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class IdempotentAspect implements Ordered {

    private final IdempotencyStore store;
    private final BeanFactory beanFactory;
    private final int order;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 创建幂等状态切面。
     *
     * @param store 幂等状态存储
     * @param beanFactory Spring Bean 工厂
     * @param order 切面顺序
     */
    public IdempotentAspect(IdempotencyStore store, BeanFactory beanFactory, int order) {
        this.store = Objects.requireNonNull(store, "幂等状态存储不能为空");
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory 不能为空");
        this.order = order;
    }

    /**
     * 获取幂等所有权并在业务真实完成后提交或释放状态。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原方法执行结果
     * @throws Throwable 原方法或状态机异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.idempotency.Idempotent)"
            + " || @within(io.github.zhanghslq.muskit.idempotency.Idempotent)")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Idempotent annotation = resolveAnnotation(method, invokedMethod, joinPoint.getTarget());
        String key = evaluateKey(annotation.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        IdempotencyClaim claim = acquire(toRequest(annotation, key));

        boolean finishSynchronously = true;
        Throwable invocationFailure = null;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> completionStage) {
                // 异步方法只有在结果真正完成后才能提交成功状态或释放失败状态。
                finishSynchronously = false;
                return completionStage.whenComplete((ignored, failure) -> finish(claim, failure));
            }
            return result;
        } catch (Throwable throwable) {
            invocationFailure = throwable;
            throw throwable;
        } finally {
            if (finishSynchronously) {
                finish(claim, invocationFailure);
            }
        }
    }

    /**
     * 返回幂等切面执行顺序。
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
        return AopUtils.getMostSpecificMethod(interfaceMethod, AopUtils.getTargetClass(joinPoint.getTarget()));
    }

    /**
     * 按具体方法、接口方法和目标类顺序解析幂等注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名方法
     * @param target 目标对象
     * @return 幂等注解
     */
    private Idempotent resolveAnnotation(Method method, Method invokedMethod, Object target) {
        Idempotent annotation = AnnotatedElementUtils.findMergedAnnotation(method, Idempotent.class);
        if (annotation == null && method != invokedMethod) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, Idempotent.class);
        }
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(target), Idempotent.class);
        }
        if (annotation == null) {
            throw new IllegalStateException("未找到幂等注解");
        }
        return annotation;
    }

    /**
     * 计算业务幂等键。
     *
     * @param keyExpression 业务幂等键 SpEL
     * @param method 被调用方法
     * @param target 目标对象
     * @param arguments 方法参数
     * @return 业务幂等键
     */
    private String evaluateKey(String keyExpression, Method method, Object target, Object[] arguments) {
        if (keyExpression == null || keyExpression.isBlank()) {
            throw new IllegalArgumentException("幂等业务键表达式不能为空");
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target, method, arguments, parameterNameDiscoverer);
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        Expression expression = expressionCache.computeIfAbsent(keyExpression, expressionParser::parseExpression);
        Object value = expression.getValue(context);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("幂等业务键计算结果不能为空");
        }
        return value.toString();
    }

    /**
     * 将注解属性转换为幂等请求。
     *
     * @param annotation 幂等注解
     * @param key 业务幂等键
     * @return 幂等请求
     */
    private IdempotencyRequest toRequest(Idempotent annotation, String key) {
        Duration processingTimeout = Duration.of(
                annotation.processingTimeout(), annotation.processingTimeoutUnit().toChronoUnit());
        Duration retention = Duration.of(annotation.retention(), annotation.retentionUnit().toChronoUnit());
        return new IdempotencyRequest(annotation.operation(), key, processingTimeout, retention);
    }

    /**
     * 获取幂等所有权并将已有状态转换为稳定公共异常。
     *
     * @param request 幂等请求
     * @return 幂等所有权声明
     */
    private IdempotencyClaim acquire(IdempotencyRequest request) {
        IdempotencyAttempt attempt = store.tryStart(request);
        if (attempt.decision() == IdempotencyDecision.IN_PROGRESS) {
            throw new IdempotencyInProgressException(request.operation());
        }
        if (attempt.decision() == IdempotencyDecision.COMPLETED) {
            throw new IdempotencyCompletedException(request.operation());
        }
        return attempt.claim().orElseThrow(() -> new IllegalStateException("幂等存储未返回所有权声明"));
    }

    /**
     * 业务成功时提交成功状态，业务失败时释放状态并保留原异常为主异常。
     *
     * @param claim 幂等所有权声明
     * @param businessFailure 业务异常，成功时为空
     */
    private void finish(IdempotencyClaim claim, Throwable businessFailure) {
        try {
            if (businessFailure == null) {
                store.complete(claim);
            } else {
                store.release(claim);
            }
        } catch (RuntimeException stateFailure) {
            if (businessFailure == null) {
                throw stateFailure;
            }
            businessFailure.addSuppressed(stateFailure);
        }
    }
}
