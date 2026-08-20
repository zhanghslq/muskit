package io.github.zhanghslq.muskit.resilience.autoconfigure;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitDecision;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicy;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitPolicyResolver;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRejectedException;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitRequest;
import io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter;
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
 * 解析 RateLimitGuard 并在业务执行前消耗一个限流令牌的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class RateLimitGuardAspect implements Ordered {

    private final RateLimiter rateLimiter;
    private final RateLimitPolicyResolver policyResolver;
    private final BeanFactory beanFactory;
    private final int order;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 创建限流注解切面。
     *
     * @param rateLimiter 限流 Provider
     * @param policyResolver 限流策略解析器
     * @param beanFactory Spring Bean 工厂
     * @param order 切面顺序
     */
    public RateLimitGuardAspect(
            RateLimiter rateLimiter,
            RateLimitPolicyResolver policyResolver,
            BeanFactory beanFactory,
            int order) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "限流 Provider 不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "限流策略解析器不能为空");
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory 不能为空");
        this.order = order;
    }

    /**
     * 业务执行前消耗令牌，被拒绝时不调用业务方法。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原业务结果
     * @throws Throwable 原业务异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard)"
            + " || @within(io.github.zhanghslq.muskit.resilience.ratelimit.RateLimitGuard)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RateLimitGuard guard = resolveGuard(method, invokedMethod, joinPoint.getTarget());
        RateLimitPolicy policy = policyResolver.resolve(guard.policy());
        String key = evaluateKey(guard.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        RateLimitDecision decision = rateLimiter.tryAcquire(new RateLimitRequest(policy, key));
        if (!decision.allowed()) {
            throw new RateLimitRejectedException(policy.name(), decision.retryAfter());
        }
        return joinPoint.proceed();
    }

    /**
     * 返回限流切面顺序。
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
     * 按具体方法、代理签名和目标类型解析限流注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名方法
     * @param target 目标对象
     * @return 限流注解
     */
    private RateLimitGuard resolveGuard(Method method, Method invokedMethod, Object target) {
        RateLimitGuard guard = AnnotatedElementUtils.findMergedAnnotation(method, RateLimitGuard.class);
        if (guard == null && method != invokedMethod) {
            guard = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, RateLimitGuard.class);
        }
        if (guard == null) {
            guard = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(target), RateLimitGuard.class);
        }
        if (guard == null) {
            throw new IllegalStateException("未找到限流注解");
        }
        return guard;
    }

    /**
     * 计算限流业务隔离键。
     *
     * @param keyExpression 业务键 SpEL
     * @param method 被调用方法
     * @param target 目标对象
     * @param arguments 方法参数
     * @return 业务键，未配置时为空
     */
    private String evaluateKey(String keyExpression, Method method, Object target, Object[] arguments) {
        if (keyExpression == null || keyExpression.isBlank()) {
            return "";
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target, method, arguments, parameterNameDiscoverer);
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        Expression expression = expressionCache.computeIfAbsent(keyExpression, expressionParser::parseExpression);
        Object value = expression.getValue(context);
        return value == null ? "" : value.toString();
    }
}
