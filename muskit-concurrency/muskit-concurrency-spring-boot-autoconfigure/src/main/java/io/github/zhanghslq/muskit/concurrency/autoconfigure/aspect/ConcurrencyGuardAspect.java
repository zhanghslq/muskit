package io.github.zhanghslq.muskit.concurrency.autoconfigure.aspect;

import io.github.zhanghslq.muskit.concurrency.annotation.ConcurrencyGuard;
import io.github.zhanghslq.muskit.concurrency.exception.ConcurrencyInterruptedException;
import io.github.zhanghslq.muskit.concurrency.exception.ConcurrencyRejectedException;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyPolicy;
import io.github.zhanghslq.muskit.concurrency.model.ConcurrencyRequest;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyLimiter;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPermit;
import io.github.zhanghslq.muskit.concurrency.spi.ConcurrencyPolicyResolver;
import io.github.zhanghslq.muskit.observation.model.MuskitMetric;
import io.github.zhanghslq.muskit.observation.model.MuskitTagKey;
import io.github.zhanghslq.muskit.observation.model.ObservationTags;
import io.github.zhanghslq.muskit.observation.spi.MuskitObservationRegistry;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
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
 * 解析 {@link ConcurrencyGuard} 并在方法执行期间持有并发额度的切面。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Aspect
public final class ConcurrencyGuardAspect implements Ordered {

    private final ConcurrencyLimiter concurrencyLimiter;
    private final ConcurrencyPolicyResolver policyResolver;
    private final BeanFactory beanFactory;
    private final MuskitObservationRegistry observationRegistry;
    private final int order;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> inflight = new ConcurrentHashMap<>();

    /**
     * 创建并发控制切面。
     *
     * @param concurrencyLimiter 并发额度提供器
     * @param policyResolver 并发策略解析器
     * @param beanFactory Spring Bean 工厂
     * @param order 切面顺序
     */
    public ConcurrencyGuardAspect(
            ConcurrencyLimiter concurrencyLimiter,
            ConcurrencyPolicyResolver policyResolver,
            BeanFactory beanFactory,
            int order) {
        this(concurrencyLimiter, policyResolver, beanFactory, order, MuskitObservationRegistry.noop());
    }

    /**
     * 创建带统一可观测性的并发控制切面。
     *
     * @param concurrencyLimiter 并发额度提供器
     * @param policyResolver 并发策略解析器
     * @param beanFactory Spring Bean 工厂
     * @param order 切面顺序
     * @param observationRegistry 统一观测注册器
     */
    public ConcurrencyGuardAspect(
            ConcurrencyLimiter concurrencyLimiter,
            ConcurrencyPolicyResolver policyResolver,
            BeanFactory beanFactory,
            int order,
            MuskitObservationRegistry observationRegistry) {
        this.concurrencyLimiter = Objects.requireNonNull(concurrencyLimiter, "并发额度提供器不能为空");
        this.policyResolver = Objects.requireNonNull(policyResolver, "并发策略解析器不能为空");
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory 不能为空");
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "统一观测注册器不能为空");
        this.order = order;
    }

    /**
     * 在受保护方法执行前获取额度，并在同步执行结束或异步任务完成后释放额度。
     *
     * @param joinPoint 被拦截的方法连接点
     * @return 原方法执行结果
     * @throws Throwable 原方法或切面执行异常
     */
    @Around("@annotation(io.github.zhanghslq.muskit.concurrency.annotation.ConcurrencyGuard)"
            + " || @within(io.github.zhanghslq.muskit.concurrency.annotation.ConcurrencyGuard)")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Method invokedMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        ConcurrencyGuard guard = resolveGuard(method, invokedMethod, joinPoint.getTarget());
        ConcurrencyPolicy policy = policyResolver.resolve(guard.policy());
        String key = evaluateKey(guard.key(), method, joinPoint.getTarget(), joinPoint.getArgs());
        ObservationTags tags = ObservationTags.of(MuskitTagKey.POLICY, policy.name());
        long acquireStartedAt = System.nanoTime();
        ConcurrencyPermit permit;
        try {
            permit = acquire(new ConcurrencyRequest(policy, key));
            observationRegistry.recordDuration(
                    MuskitMetric.CONCURRENCY_ACQUIRE,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - acquireStartedAt)),
                    tags.and(MuskitTagKey.OUTCOME, "acquired"));
        } catch (RuntimeException failure) {
            observationRegistry.increment(
                    MuskitMetric.CONCURRENCY_REJECTED,
                    tags.and(MuskitTagKey.OUTCOME, "rejected"));
            throw failure;
        }
        adjustInflight(policy.name(), 1L, tags);

        boolean closeSynchronously = true;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> completionStage) {
                // 异步方法返回不代表资源使用结束，额度随异步结果的真实完成信号释放。
                closeSynchronously = false;
                return completionStage.whenComplete(
                        (ignored, throwable) -> closePermit(permit, policy.name(), tags));
            }
            return result;
        } finally {
            if (closeSynchronously) {
                closePermit(permit, policy.name(), tags);
            }
        }
    }

    /**
     * 关闭并发额度并在释放完成后更新在途数量。
     *
     * @param permit 并发额度
     * @param policyName 策略名称
     * @param tags 指标标签
     */
    private void closePermit(ConcurrencyPermit permit, String policyName, ObservationTags tags) {
        try {
            permit.close();
        } finally {
            adjustInflight(policyName, -1L, tags);
        }
    }

    /**
     * 原子调整策略维度的在途数量并发布仪表值。
     *
     * @param policyName 策略名称
     * @param delta 变化量
     * @param tags 指标标签
     */
    private void adjustInflight(String policyName, long delta, ObservationTags tags) {
        AtomicLong current = inflight.computeIfAbsent(policyName, ignored -> new AtomicLong());
        long value = current.updateAndGet(previous -> Math.max(0L, previous + delta));
        observationRegistry.setGauge(MuskitMetric.CONCURRENCY_INFLIGHT, value, tags);
        if (value == 0L) {
            inflight.remove(policyName, current);
        }
    }

    /**
     * 返回切面执行顺序，默认在事务切面外层获取并发额度。
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
     * 按方法、接口方法和目标类的顺序解析并发控制注解。
     *
     * @param method 最具体方法
     * @param invokedMethod 代理签名上的原始方法
     * @param target 目标对象
     * @return 并发控制注解
     */
    private ConcurrencyGuard resolveGuard(Method method, Method invokedMethod, Object target) {
        ConcurrencyGuard guard = AnnotatedElementUtils.findMergedAnnotation(method, ConcurrencyGuard.class);
        if (guard == null && method != invokedMethod) {
            guard = AnnotatedElementUtils.findMergedAnnotation(invokedMethod, ConcurrencyGuard.class);
        }
        if (guard == null) {
            guard = AnnotatedElementUtils.findMergedAnnotation(
                    AopUtils.getTargetClass(target), ConcurrencyGuard.class);
        }
        if (guard == null) {
            throw new IllegalStateException("未找到并发控制注解");
        }
        return guard;
    }

    /**
     * 计算注解中声明的业务键表达式。
     *
     * @param keyExpression 业务键 SpEL 表达式
     * @param method 被调用方法
     * @param target 目标对象
     * @param arguments 方法参数
     * @return 业务键，未配置表达式时返回空字符串
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
     * 获取并发额度，并将超时或中断转换为稳定的公共异常。
     *
     * @param request 并发额度请求
     * @return 已获取的并发额度
     */
    private ConcurrencyPermit acquire(ConcurrencyRequest request) {
        try {
            Optional<ConcurrencyPermit> permit = concurrencyLimiter.tryAcquire(request);
            return permit.orElseThrow(() -> new ConcurrencyRejectedException(request.policy().name()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConcurrencyInterruptedException(request.policy().name(), exception);
        }
    }
}
