# Muskit

Muskit 是面向 Java 21+ 与 Spring Boot 的服务可靠性和并发治理工具箱。项目通过独立 starter 提供可插拔能力，核心模块保持轻量，并允许应用替换底层实现。

当前版本：`0.1.0-SNAPSHOT`

## 环境要求

- Java 21 或更高版本
- Maven 3.8.6 或更高版本
- Spring Boot 4.1.x

项目以 Java 21 编译。虚拟线程场景推荐使用 JDK 25，但公共 API 不使用 Java preview 特性。

## 已实现模块

| 模块 | 说明 |
| --- | --- |
| `muskit-bom` | 统一管理 Muskit 模块版本 |
| `muskit-context-core` | 不可变业务上下文和线程作用域 |
| `muskit-spring-boot-starter-context` | 基于 Micrometer Context Propagation 的 Spring 任务上下文传播 |
| `muskit-concurrency-core` | 并发策略、SPI 和本地 Semaphore 实现 |
| `muskit-spring-boot-starter-concurrency` | `@ConcurrencyGuard` 注解、SpEL 业务键和 Spring Boot 自动配置 |
| `muskit-concurrency-redis-adapter` | 基于 Redis 原子租约令牌和自动续期的跨实例并发上限 |
| `muskit-spring-boot-starter-concurrency-redis` | Redisson 强依赖的分布式并发控制 Starter |
| `muskit-lock-core` | 分布式锁注解、SPI、请求模型和显式本地降级实现 |
| `muskit-lock-redis-adapter` | 基于 Redisson 的 Redis 分布式锁实现 |
| `muskit-spring-boot-starter-lock` | Redis 强依赖的分布式锁 Starter 和 Spring Boot 自动配置 |
| `muskit-idempotency-core` | 幂等注解、PROCESSING/COMPLETED 状态机和状态存储 SPI |
| `muskit-idempotency-jdbc-adapter` | 基于唯一键和所有权令牌的 JDBC 幂等状态存储 |
| `muskit-idempotency-redis-adapter` | 基于 Redis Lua 脚本的原子幂等状态存储 |
| `muskit-spring-boot-starter-idempotency-jdbc` | JDBC 幂等状态 Starter |
| `muskit-spring-boot-starter-idempotency-redis` | Redis 幂等状态 Starter |
| `muskit-idempotency-kafka-adapter` | 以 topic/partition/offset 为键的 Kafka 消费幂等拦截器 |
| `muskit-idempotency-rabbit-adapter` | RabbitListener Advice Chain 消息幂等拦截器 |
| `muskit-idempotency-http-spring-adapter` | 支持同步和异步 Servlet 生命周期的 HTTP 幂等 Filter |
| `muskit-resilience-core` | 限流、Retry、Circuit Breaker、SingleFlight、Deadline 的公共 API 与 SPI |
| `muskit-resilience-redis-adapter` | 使用 Redis TIME 和 Lua 原子脚本实现的跨实例令牌桶 |
| `muskit-resilience4j-adapter` | 基于 Resilience4j 的 Circuit Breaker Provider |
| `muskit-spring-boot-starter-resilience` | 本地限流、`@RetryGuard` 与 Deadline 协作能力 |
| `muskit-spring-boot-starter-resilience-redis` | Redis 强依赖的分布式限流 Starter |
| `muskit-spring-boot-starter-resilience4j` | `@CircuitBreakerGuard` 和 Resilience4j Provider Starter |
| `muskit-observation-core` | 统一低基数指标目录和可替换观测 SPI |
| `muskit-observation-micrometer-adapter` | Muskit 指标到 Micrometer 的适配 |
| `muskit-spring-boot-starter-observability` | 统一指标与 `/actuator/muskit` 能力快照 |
| `muskit-spring-boot-starter-lifecycle` | Readiness 摘流、HTTP 在途请求统计和有界优雅排空 |
| `muskit-spring-boot-starter-executor` | 有界平台/虚拟线程执行器、上下文传播和排空治理 |
| `muskit-inbox-core` | 可靠消费状态机、退避重试、死信和人工回放 SPI |
| `muskit-spring-boot-starter-inbox-jdbc` | JDBC Inbox 可靠消费 Starter |
| `muskit-cache-core` | 防击穿、空值缓存、TTL 抖动和 stale-while-revalidate |
| `muskit-spring-boot-starter-cache-redis` | Redis 强依赖的可靠缓存 Starter |
| `muskit-spring-boot-starter-client` | RestClient Deadline 与业务上下文白名单传播 |
| `muskit-audit-core` | 审计注解、程序化 API、Writer 和失败模式 SPI |
| `muskit-spring-boot-starter-audit-jdbc` | JDBC 审计事件持久化 Starter |
| `muskit-state-core` | 不可变状态机定义、Guard 和乐观锁仓储执行器 |
| `muskit-spring-boot-starter-state` | 状态机工厂自动配置 |
| `muskit-outbox-core` | Transactional Outbox 事件、事务存储和发布 SPI |
| `muskit-outbox-jdbc-adapter` | 使用条件更新和有期限租约的 JDBC Outbox 存储 |
| `muskit-outbox-kafka-adapter` | 等待 broker 确认的 Kafka Outbox 发布器 |
| `muskit-spring-boot-starter-outbox` | JDBC + Kafka Transactional Outbox 自动配置与后台轮询 |
| `muskit-test-support` | 基于虚拟线程的并发测试辅助工具 |

## 引入依赖

应用项目可以先导入 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.zhanghslq</groupId>
            <artifactId>muskit-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

然后按需选择 starter：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-context</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-concurrency</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-lock</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-idempotency-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-resilience</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-resilience4j</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-outbox</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-observability</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-lifecycle</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-executor</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-inbox-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-cache-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-client</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-audit-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.zhanghslq</groupId>
        <artifactId>muskit-spring-boot-starter-state</artifactId>
    </dependency>
</dependencies>
```

这里只用于展示坐标，实际应用应按需引入；特别是 Redis、JDBC、Kafka Starter 不应为了“全家桶”一次性加入。

## 统一可观测性、生命周期和执行器

`muskit-spring-boot-starter-observability` 将锁、幂等、并发、限流、Retry、熔断、Inbox、Outbox、缓存、执行器、客户端和审计统一接入 `MuskitObservationRegistry`。Micrometer 指标只使用 `policy`、`provider`、`operation`、`outcome` 等低基数标签；业务 Key、租户 ID 和消息 ID 不会成为标签。启用 Actuator 暴露后可以访问 `/actuator/muskit` 查看已装配能力，不输出业务键。

```yaml
muskit:
  observability:
    enabled: true
    endpoint-enabled: true
  lifecycle:
    shutdown-timeout: 30s
    excluded-path-prefixes:
      - /actuator/health
  executor:
    executors:
      default:
        type: virtual
        max-concurrency: 100
        queue-capacity: 200
        shutdown-timeout: 30s
```

Lifecycle 在停止阶段先发布 `ReadinessState.REFUSING_TRAFFIC`，拒绝新的普通 HTTP 请求，再使用同一总超时预算排空在途请求和受管执行器。`ManagedTaskExecutor` 即使使用虚拟线程也会限制最大并发和等待容量，并传播 `MuskitContext` 与 `DeadlineContext`。

## 业务上下文

MuskitContext 用于 tenantId、operatorId、correlationId 等业务信息。traceId、tracestate 和 baggage 仍由 Micrometer Tracing 管理。

```java
MuskitContext context = MuskitContext.of(Map.of("tenantId", "tenant-1"));
try (MuskitContextHolder.Scope ignored = MuskitContextHolder.open(context)) {
    // 在当前作用域内执行业务代码
}
```

Context starter 会注册 Micrometer `ThreadLocalAccessor` 和 Spring `TaskDecorator`。如果应用已经提供自己的 `TaskDecorator`，自动配置会主动退让。

```yaml
muskit:
  context:
    enabled: true
    task-decorator-enabled: true
```

## 并发控制

在配置文件中定义资源策略：

```yaml
muskit:
  concurrency:
    policies:
      payment-api:
        max-concurrency: 100
        max-wait: 200ms
        scope: global
      tenant-export:
        max-concurrency: 2
        max-wait: 100ms
        scope: key
        fair: false
```

方法或类通过策略名称引用配置，按 Key 隔离时使用 SpEL 计算业务键：

```java
/**
 * 执行指定租户的数据导出。
 *
 * @param tenantId 租户标识
 * @return 导出结果
 */
@ConcurrencyGuard(policy = "tenant-export", key = "#tenantId")
public String export(String tenantId) {
    return "exported:" + tenantId;
}
```

当前并发控制支持：

- 全局和按业务 Key 隔离
- 公平或非公平 Semaphore
- 零等待快速失败和有界等待
- 平台线程及虚拟线程
- 同步方法
- `CompletionStage`：在异步任务真正完成后释放额度
- 自定义 `ConcurrencyLimiter` 和 `ConcurrencyPolicyResolver` Bean

获取额度超时时抛出 `ConcurrencyRejectedException`。等待期间被中断时会恢复线程中断标记并抛出 `ConcurrencyInterruptedException`。

当前版本不支持 Reactor Publisher；对返回 `Mono` 或 `Flux` 的方法使用该注解只会覆盖 Publisher 创建过程，因此不应这样使用。

### Redis 分布式并发控制

需要多个应用实例共同遵守同一个并发上限时，引入 `muskit-spring-boot-starter-concurrency-redis` 并显式选择 Redis：

```yaml
muskit:
  concurrency:
    provider: redis
    redis-key-prefix: "muskit:concurrency:"
    redis-lease-time: 30s
    policies:
      payment-api:
        max-concurrency: 20
        max-wait: 100ms
        scope: global
        fair: false
```

Redis Provider 使用原子脚本分配带租约的唯一额度令牌，业务执行期间每隔约三分之一租约自动续期，实例失联后额度会自动过期。业务 Key 只以 SHA-256 摘要进入 Redis Key。选择 `provider=redis` 但缺少 `RedissonClient` 时应用启动失败，不会降级为本地并发控制。当前 Redis Provider 不支持公平队列；策略设置 `fair=true` 会明确失败。

## Redis 分布式锁

锁 Starter 强依赖 Redisson，并通过 Spring Boot 的 `spring.data.redis` 配置创建 `RedissonClient`。默认没有本地实现兜底：缺少 `RedissonClient` 时应用启动失败，Redis 获取锁异常时业务调用失败。

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

muskit:
  lock:
    enabled: true
    key-prefix: "muskit:lock:"
```

业务可以在方法或类上声明锁名、SpEL 业务键、等待时间、租约、公平锁和本地降级策略：

```java
/**
 * 提交指定订单。
 *
 * @param orderId 订单标识
 * @return 提交结果
 */
@DistributedLock(
        name = "order-submit",
        key = "#orderId",
        waitTime = 500,
        leaseTime = 30_000,
        timeUnit = TimeUnit.MILLISECONDS,
        fair = true,
        localFallback = false,
        fencing = true)
public String submit(String orderId) {
    long fencingToken = FencingTokenContext.current().orElseThrow();
    return "submitted:" + orderId + ":token=" + fencingToken;
}
```

- `waitTime = 0` 表示立即尝试；在等待时间内没有获得锁时抛出 `DistributedLockRejectedException`。
- `leaseTime = -1` 使用 Redisson 看门狗自动续期；正数表示固定租约。
- `localFallback = false` 是默认值，Redis 异常时保持失败语义。
- 只有显式设置 `localFallback = true` 才会在 Redis 获取异常时使用本地锁，并输出 WARN，明确提示此时锁仅当前 JVM 生效、跨实例互斥不再保证。其他实例已持有 Redis 锁造成的正常竞争失败不会触发降级。
- `fencing = true` 时，每次成功获取 Redis 锁都会得到严格递增令牌。业务必须把令牌写入下游资源的条件更新，拒绝小于已见最大值的旧持有者；fencing 模式禁止本地降级，Redis 或令牌计数器不可用时明确失败。
- 本地降级锁不使用固定租约提前释放，而是在同步方法结束或 `CompletionStage` 真正完成后释放。

锁名称应是稳定的低基数业务名称，业务锁键不会进入公共异常、日志或 `toString()`。每次注解调用使用独立的锁 owner，线程池复用不会被误判为锁重入；同一调用链嵌套获取相同锁也不会按重入处理。当前锁切面与并发切面一样支持同步方法和 `CompletionStage`，不支持 Reactor Publisher。

如果应用存在 `MeterRegistry`，锁 Starter 自动记录以下低基数指标，不会把业务锁 Key 放入标签：

- `muskit.lock.acquire`：获取耗时，标签为 `lock`、`provider`、`outcome`。
- `muskit.lock.fallback`：显式本地降级次数，标签为 `lock`、`provider=local-fallback`、`outcome=activated`。

## 幂等状态机

Redis 与 JDBC Starter 共享同一套 `PROCESSING -> COMPLETED` 状态机。调用开始时原子获取带所有权令牌的处理中记录；业务成功后提交完成状态，业务异常时释放记录；处理中超时或完成状态保留期到期后可以重新执行。`CompletionStage` 只有在异步结果真正完成后才提交或释放状态。

Redis 使用方式：

```yaml
muskit:
  idempotency:
    enabled: true
    provider: redis
    redis-key-prefix: "muskit:idempotency:"
    lease-renewal-enabled: true
```

JDBC 使用方式：

```yaml
muskit:
  idempotency:
    enabled: true
    provider: jdbc
    jdbc-table-name: muskit_idempotency
    initialize-schema: true
```

生产环境建议通过数据库迁移工具创建表，并关闭 `initialize-schema`。使用注解时，`operation` 必须是稳定低基数名称，`key` 为 SpEL 业务键：

```java
@Idempotent(operation = "order-create", key = "#request.requestId")
public CompletionStage<Order> create(OrderRequest request) {
    return orderGateway.create(request);
}
```

重复处理中调用抛出 `IdempotencyInProgressException`，已经成功的重复调用抛出 `IdempotencyCompletedException`。业务幂等键和所有权令牌不会进入公共异常或 `toString()`；Redis Key 使用业务键 SHA-256 摘要。

长任务可以显式开启 `lease-renewal-enabled`。Redis 和 JDBC Provider 都会在校验所有权后原子续期；续期失败会在业务完成时明确报告，默认保持关闭以兼容未实现 `renew` 的自定义 Provider。除注解外，`IdempotencyTemplate.execute(...)` 以明确的 `businessId` 执行单条业务，`executeBatch(...)` 会逐项获取所有权，并返回不包含业务 ID 的完成、重复和处理中数量汇总。

## Reliable Inbox

`muskit-spring-boot-starter-inbox-jdbc` 用业务消息 ID 驱动 `PROCESSING / RETRY_WAIT / SUCCEEDED / DEAD` 状态机，处理失败时指数退避，达到最大次数进入死信，支持人工回放：

```yaml
muskit:
  inbox:
    provider: jdbc
    table-name: muskit_inbox
    initialize-schema: false
    policies:
      order-consumer:
        processing-timeout: 30s
        retention: 7d
        max-attempts: 5
        initial-retry-delay: 1s
        retry-multiplier: 2
        max-retry-delay: 5m
```

```java
InboxProcessResult result = inboxTemplate.process(
        "order-consumer", messageId, "order-consumer", () -> handle(message));
```

业务消息 ID 只用于存储条件，不进入指标、公共异常或安全描述。消息处于重试等待、处理中冲突、完成和死信时都有明确结果，不会把毒消息无限快速重试。

## 可靠缓存

`muskit-spring-boot-starter-cache-redis` 提供进程内 SingleFlight 防击穿、独立空值 TTL 防穿透、TTL 抖动防雪崩，以及可选 stale-while-revalidate：

```yaml
muskit:
  cache:
    provider: redis
    key-prefix: "muskit:cache"
    refresh-executor: default
    policies:
      product:
        ttl: 10m
        null-ttl: 30s
        ttl-jitter-ratio: 0.1
        stale-while-revalidate: 1m
        failure-mode: fail-fast
```

默认 Redis 异常直接失败。只有显式选择 `load-without-cache` 才允许绕过缓存加载数据；删除缓存始终失败即报错，避免把失效失败伪装成成功。业务 Key 以 SHA-256 摘要进入 Redis Key。

## HTTP 客户端调用链治理

`muskit-spring-boot-starter-client` 自动定制 Spring `RestClient.Builder`，覆盖写入绝对 Deadline 请求头，并只传播配置白名单内的 `MuskitContext`：

```yaml
muskit:
  client:
    outbound-timeout: 3s
    max-inbound-timeout: 30s
    operation: remote-http
    context-headers:
      tenantId: X-Tenant-Id
      operatorId: X-Operator-Id
```

子调用 Deadline 只能缩短，不能延长父预算；非法、过期或包含请求头注入字符的传播值会被拒绝。Servlet 入站过滤器在请求结束后恢复原线程状态，客户端指标不会使用 URL 或业务 ID 作为标签。

## 审计与状态机

JDBC 审计支持程序化 `AuditRecorder` 和 `@Audited(action = "order.cancel")`。注解不自动序列化方法参数，避免敏感参数意外入库；程序化 API 可显式传入主体与扩展属性。默认 `FAIL_FAST`，只有配置 `BEST_EFFORT` 才允许业务继续，同时记录 `dropped` 指标并输出 WARN。

```yaml
muskit:
  audit:
    provider: jdbc
    table-name: muskit_audit
    failure-mode: fail-fast
  state:
    max-conflict-retries: 3
```

`StateMachineDefinition` 保证同一“来源状态 + 事件”只有一条规则，Guard 拒绝、无规则和成功迁移返回不同状态。`PersistentStateMachine` 通过业务提供的 `StateRepository.compareAndSet(...)` 原子提交，并在冲突后重新读取最新状态；实体 ID 不进入冲突异常。

### Kafka、RabbitMQ 和 HTTP

- Kafka：将 `KafkaIdempotencyRecordInterceptor` 配置到 Listener Container。它使用 topic、partition、offset 组合键；成功时完成状态，监听器失败时释放状态，已完成记录返回 `null` 跳过处理。
- RabbitMQ：将 `RabbitIdempotencyAdvice` 加入 Listener Container Factory 的 Advice Chain。默认要求生产者设置稳定 `messageId`，也可以提供 `RabbitMessageKeyResolver`；当前不支持批量监听器。
- HTTP：按需要保护的 URL 注册 `HttpIdempotencyFilter`。默认从 `Idempotency-Key` 请求头读取键，也会等待异步 Servlet 请求真实完成。缺少键返回 `400`，处理中返回 `409`。成功响应默认缓存不超过 64 KiB 的 JSON/空响应，以及白名单中的 `Location`、`ETag` 响应头；完成态重复请求会原样重放并返回 `Idempotency-Status: replayed`。响应体超过上限或内容类型不允许时仍提交完成状态，重复请求返回 `409` 和 `completed-not-replayable`，不会悄悄重新执行业务。

HTTP 响应回放要求底层 Store 支持结果存储。Redis Store 已直接支持；JDBC Store 的表新增了可空 `result_data BLOB` 列。已有数据库必须先通过迁移工具增加该列，`initialize-schema` 只负责创建新表，不会修改已有表结构。完整构造器允许调整最大响应字节数、响应头白名单和可回放 Content-Type 策略。

## 限流、SingleFlight 和 Deadline

令牌桶限流配置示例：

```yaml
muskit:
  resilience:
    rate-limit-enabled: true
    rate-limit-provider: local
    max-local-buckets: 100000
    local-bucket-idle-retention: 10m
    rate-limit-policies:
      tenant-api:
        capacity: 20
        refill-tokens: 10
        refill-period: 1s
        scope: key
```

```java
@RateLimitGuard(policy = "tenant-api", key = "#tenantId")
public String invoke(String tenantId) {
    return "ok";
}
```

本地令牌桶使用单调时钟连续补充令牌，并通过最大桶数量与空闲清理限制按 Key 隔离的内存占用；它只保证单 JVM 限流。令牌不足时抛出 `RateLimitRejectedException`，异常携带策略名称和建议等待时间，不携带业务 Key。应用可以提供自己的 `RateLimiter` Bean 替换默认实现。

多实例共享限流配额时改用 `muskit-spring-boot-starter-resilience-redis`：

```yaml
muskit:
  resilience:
    rate-limit-provider: redis
    redis-rate-limit-key-prefix: "muskit:rate-limit:"
    rate-limit-policies:
      tenant-api:
        capacity: 100
        refill-tokens: 50
        refill-period: 1s
        scope: key
```

Redis Provider 以 Redis 服务端时间为准，通过 Lua 原子完成补充、判断和扣减；业务 Key 只以 SHA-256 摘要进入 Redis Key。显式选择 `redis` 但缺少 `RedissonClient` 时应用启动失败，Redis 异常时抛出 `RateLimitBackendException`，不会降级到单 JVM 限流。

### Retry 与 Deadline

```yaml
muskit:
  resilience:
    retry-enabled: true
    retry-policies:
      remote-payment:
        max-attempts: 3
        initial-delay: 100ms
        multiplier: 2
        max-delay: 1s
        jitter: 0.2
        retry-on:
          - java.io.IOException
        abort-on:
          - java.lang.IllegalArgumentException
```

```java
@RetryGuard(policy = "remote-payment")
public CompletionStage<PaymentResult> pay() {
    return paymentGateway.pay();
}
```

Retry 支持同步方法和 `CompletionStage`，使用有上限的指数退避和随机抖动；中断时恢复线程中断标记并停止重试。存在 `DeadlineContext` 时，每次重试都传播当前 Deadline，若剩余预算不足以容纳下一次等待则直接抛出 `DeadlineExceededException`，不会启动注定超时的新尝试。

### Circuit Breaker

引入 `muskit-spring-boot-starter-resilience4j` 后按策略使用 `@CircuitBreakerGuard`：

```yaml
muskit:
  resilience:
    circuit-breaker-policies:
      remote-payment:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 2s
        minimum-number-of-calls: 10
        sliding-window-size: 100
        permitted-calls-in-half-open: 5
        wait-duration-in-open-state: 30s
```

```java
@CircuitBreakerGuard(policy = "remote-payment")
public CompletionStage<PaymentResult> pay() {
    return paymentGateway.pay();
}
```

Provider 支持关闭、开启、半开状态以及失败率和慢调用率判定；`CompletionStage` 只有在异步任务真实完成后才记录结果。策略名用于低基数隔离，业务 Key 不参与熔断器命名。

`SingleFlight<K, V>` 将同一业务键当前并发的同步或异步动作合并为一次真实执行。每个调用方获得独立结果视图，单个调用方取消不会取消共享任务；成功或失败后占位都会清理，后续请求可以重新执行。

```java
SingleFlight<String, Product> singleFlight = new SingleFlight<>();
CompletionStage<Product> result = singleFlight.execute(productId, () -> repository.load(productId));
```

`DeadlineContext` 使用嵌套作用域传递调用预算，子作用域只能缩短、不能延长父 Deadline。作用域必须按顺序在创建线程关闭；提交到普通线程池时使用 `DeadlineContext.wrap(...)` 显式捕获和恢复上下文。

```java
try (DeadlineContext.Scope ignored = DeadlineContext.open(Deadline.after(Duration.ofSeconds(2)))) {
    DeadlineContext.check();
    executor.execute(DeadlineContext.wrap(task));
}
```

## Transactional Outbox

`muskit-spring-boot-starter-outbox` 用于把业务数据库变更与待发布事件写入同一个本地事务，再由后台轮询器至少一次发布到 Kafka：

```yaml
muskit:
  outbox:
    enabled: true
    table-name: muskit_outbox
    initialize-schema: false
    require-transaction: true
    batch-size: 100
    lease-time: 30s
    retry-delay: 5s
    max-attempts: 10
    retry-multiplier: 2
    max-retry-delay: 5m
    poll-interval: 1s
    published-retention: 7d
```

```java
@Transactional
public UUID createOrder(Order order) {
    orderRepository.save(order);
    return outboxService.publish(new OutboxMessageRequest(
            "orders",
            order.id(),
            serializer.serialize(order),
            Map.of("event-type", "order-created")));
}
```

默认 `require-transaction=true`，脱离活动数据库事务写入会明确失败。JDBC Store 通过条件更新竞争有期限租约，同一 `partitionKey` 严格按创建顺序发布；Kafka Publisher 等待 broker 确认后才标记 `PUBLISHED`。失败使用有上限指数退避，耗尽后进入 `DEAD` 并通知可替换 `OutboxDeadLetterSink`，人工修复后可调用 `replayDead(...)`。DEAD 事件会阻塞同一分区后续事件，避免悄悄破坏聚合顺序。若 Kafka 已成功而数据库确认失败，事件会再次投递，因此消费者仍必须幂等。生产环境应通过 Flyway/Liquibase 创建表并关闭 `initialize-schema`；自动建表只适合本地开发与测试。轮询器可用 `scheduler-enabled=false` 关闭，随后由应用自行调用 `OutboxDispatchService.dispatchBatch()`。

## 构建

```shell
./mvnw clean verify
```

示例应用位于 [`examples/muskit-example`](examples/muskit-example)。

## 模块规则

- core 不依赖 Spring Boot
- adapter 只依赖对应 core 和底层客户端
- autoconfigure 只负责条件装配、属性绑定和框架适配
- starter 只组合依赖
- 不在后端不可用时隐式降级为更弱的本地语义

完整边界见 [`docs/architecture/module-boundaries.md`](docs/architecture/module-boundaries.md)。

## 已完成路线

- `0.2.x`：分布式锁、Micrometer 可观测性和 Provider 契约测试
- `0.3.x`：幂等状态机、JDBC/Redis 存储、Kafka 消费适配
- `0.4.x`：RabbitMQ、HTTP 幂等、Redis 分布式并发控制
- `0.5.x`：令牌桶限流、SingleFlight、Deadline
- `0.6.x`：Redis 分布式限流、HTTP 响应回放、Retry、Circuit Breaker、Transactional Outbox
- `0.7.x`：统一可观测性、生命周期、受管执行器、Reliable Inbox
- `0.8.x`：可靠缓存、HTTP 客户端治理、审计、状态机、fencing token 与可靠性增强

## 发布到 Maven Central

项目发布坐标为 `io.github.zhanghslq`，Java 包名以 `io.github.zhanghslq.muskit` 开头。发布元数据指向 GitHub 仓库，并包含 Apache License 2.0、开发者和 SCM 信息。

首次发布前需要完成：

1. 使用 GitHub 账号登录 [Central Publisher Portal](https://central.sonatype.com/)，确认 `io.github.zhanghslq` namespace 已验证。
2. 在 Portal 创建 User Token，并在 Maven `settings.xml` 中配置 ID 为 `central` 的 server。
3. 创建并公开 GPG 公钥，确保本机 `gpg-agent` 可用于签名。
4. 将项目版本从 `SNAPSHOT` 调整为未发布过的正式版本，然后执行发布命令。

```xml
<server>
    <id>central</id>
    <username>${env.MAVEN_CENTRAL_USERNAME}</username>
    <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
</server>
```

发布前通过安全的环境变量或 CI Secret 注入 Portal Token 与 `MAVEN_GPG_PASSPHRASE`，不要把凭据或私钥提交到仓库。

```shell
./mvnw -B -ntp -Prelease clean deploy
```

`release` profile 会为可发布模块生成源码包和 Javadoc 包、使用 GPG 签名，并通过 Central Publishing Maven Plugin 上传；`muskit-example` 仅作为 Git 仓库中的示例项目，不发布到 Maven Central。配置默认关闭自动发布，Portal 校验通过后仍需人工确认 Publish。

## License

Muskit 使用 Apache License 2.0。
