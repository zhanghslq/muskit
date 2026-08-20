package io.github.zhanghslq.muskit.observation.autoconfigure;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * 只展示 Provider、策略名称和启用状态的低基数 Muskit 运行快照。
 *
 * @author zhs
 * @since 2026-08-20
 */
@Endpoint(id = "muskit")
public final class MuskitEndpoint {

    private static final String CONCURRENCY_LIMITER =
            "io.github.zhanghslq.muskit.concurrency.ConcurrencyLimiter";
    private static final String DISTRIBUTED_LOCK_PROVIDER =
            "io.github.zhanghslq.muskit.lock.DistributedLockProvider";
    private static final String IDEMPOTENCY_STORE =
            "io.github.zhanghslq.muskit.idempotency.IdempotencyStore";
    private static final String RATE_LIMITER =
            "io.github.zhanghslq.muskit.resilience.ratelimit.RateLimiter";
    private static final String RETRY_EXECUTOR =
            "io.github.zhanghslq.muskit.resilience.retry.RetryExecutor";
    private static final String CIRCUIT_BREAKER =
            "io.github.zhanghslq.muskit.resilience.circuitbreaker.CircuitBreaker";
    private static final String OUTBOX_SERVICE =
            "io.github.zhanghslq.muskit.outbox.OutboxService";

    private final ListableBeanFactory beanFactory;
    private final Environment environment;

    /**
     * 创建 Muskit Actuator Endpoint。
     *
     * @param beanFactory Spring Bean 工厂
     * @param environment Spring 配置环境
     */
    public MuskitEndpoint(ListableBeanFactory beanFactory, Environment environment) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory 不能为空");
        this.environment = Objects.requireNonNull(environment, "Environment 不能为空");
    }

    /**
     * 返回不包含业务键、消息标识和租户标识的运行快照。
     *
     * @return Muskit 运行快照
     */
    @ReadOperation
    public Map<String, Object> muskit() {
        Map<String, Object> components = new LinkedHashMap<>();
        addComponent(components, "lock", DISTRIBUTED_LOCK_PROVIDER, "redis", List.of());
        addComponent(
                components,
                "idempotency",
                IDEMPOTENCY_STORE,
                environment.getProperty("muskit.idempotency.provider", "redis"),
                List.of());
        addComponent(
                components,
                "concurrency",
                CONCURRENCY_LIMITER,
                environment.getProperty("muskit.concurrency.provider", "local"),
                readPolicyNames(
                        "io.github.zhanghslq.muskit.concurrency.autoconfigure.MuskitConcurrencyProperties",
                        "getPolicies"));
        addComponent(
                components,
                "rateLimit",
                RATE_LIMITER,
                environment.getProperty("muskit.resilience.rate-limit-provider", "local"),
                readPolicyNames(
                        "io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties",
                        "getRateLimitPolicies"));
        addComponent(
                components,
                "retry",
                RETRY_EXECUTOR,
                "built-in",
                readPolicyNames(
                        "io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties",
                        "getRetryPolicies"));
        addComponent(
                components,
                "circuitBreaker",
                CIRCUIT_BREAKER,
                "resilience4j",
                readPolicyNames(
                        "io.github.zhanghslq.muskit.resilience.autoconfigure.MuskitResilienceProperties",
                        "getCircuitBreakerPolicies"));
        addComponent(components, "outbox", OUTBOX_SERVICE, "jdbc-kafka", List.of());
        return Map.of("components", components);
    }

    /**
     * 存在对应运行 Bean 时增加组件快照。
     *
     * @param components 组件结果
     * @param component 组件名称
     * @param beanTypeName 运行 Bean 类型名称
     * @param provider Provider 名称
     * @param policies 稳定策略名称
     */
    private void addComponent(
            Map<String, Object> components,
            String component,
            String beanTypeName,
            String provider,
            List<String> policies) {
        if (!hasBean(beanTypeName)) {
            return;
        }
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("enabled", true);
        descriptor.put("provider", provider);
        if (!policies.isEmpty()) {
            descriptor.put("policies", policies);
        }
        components.put(component, Map.copyOf(descriptor));
    }

    /**
     * 按类型名称判断 Spring 容器是否存在对应 Bean。
     *
     * @param beanTypeName Bean 类型名称
     * @return 是否存在
     */
    private boolean hasBean(String beanTypeName) {
        if (!ClassUtils.isPresent(beanTypeName, beanFactory.getClass().getClassLoader())) {
            return false;
        }
        Class<?> type = ClassUtils.resolveClassName(beanTypeName, beanFactory.getClass().getClassLoader());
        return beanFactory.getBeanNamesForType(type, false, false).length > 0;
    }

    /**
     * 通过可选配置 Bean 读取稳定策略名称，不建立模块间编译依赖。
     *
     * @param propertiesTypeName 配置类型名称
     * @param accessor 策略映射访问方法
     * @return 排序后的策略名称
     */
    private List<String> readPolicyNames(String propertiesTypeName, String accessor) {
        if (!ClassUtils.isPresent(propertiesTypeName, beanFactory.getClass().getClassLoader())) {
            return List.of();
        }
        try {
            Class<?> type = ClassUtils.resolveClassName(
                    propertiesTypeName, beanFactory.getClass().getClassLoader());
            String[] beanNames = beanFactory.getBeanNamesForType(type, false, false);
            if (beanNames.length == 0) {
                return List.of();
            }
            Object properties = beanFactory.getBean(beanNames[0]);
            Method method = type.getMethod(accessor);
            Object value = method.invoke(properties);
            if (!(value instanceof Map<?, ?> policies)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            policies.keySet().stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .sorted()
                    .forEach(names::add);
            return List.copyOf(names);
        } catch (ReflectiveOperationException exception) {
            return List.of();
        }
    }
}
