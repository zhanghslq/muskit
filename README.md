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
| `muskit-resilience-core` | 本地令牌桶、SingleFlight、Deadline 作用域和 SPI |
| `muskit-spring-boot-starter-resilience` | `@RateLimitGuard` 和限流策略自动配置 |
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
</dependencies>
```

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
        localFallback = false)
public String submit(String orderId) {
    return "submitted:" + orderId;
}
```

- `waitTime = 0` 表示立即尝试；在等待时间内没有获得锁时抛出 `DistributedLockRejectedException`。
- `leaseTime = -1` 使用 Redisson 看门狗自动续期；正数表示固定租约。
- `localFallback = false` 是默认值，Redis 异常时保持失败语义。
- 只有显式设置 `localFallback = true` 才会在 Redis 获取异常时使用本地锁，并输出 WARN，明确提示此时锁仅当前 JVM 生效、跨实例互斥不再保证。其他实例已持有 Redis 锁造成的正常竞争失败不会触发降级。
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

### Kafka、RabbitMQ 和 HTTP

- Kafka：将 `KafkaIdempotencyRecordInterceptor` 配置到 Listener Container。它使用 topic、partition、offset 组合键；成功时完成状态，监听器失败时释放状态，已完成记录返回 `null` 跳过处理。
- RabbitMQ：将 `RabbitIdempotencyAdvice` 加入 Listener Container Factory 的 Advice Chain。默认要求生产者设置稳定 `messageId`，也可以提供 `RabbitMessageKeyResolver`；当前不支持批量监听器。
- HTTP：按需要保护的 URL 注册 `HttpIdempotencyFilter`。默认从 `Idempotency-Key` 请求头读取键，支持自定义操作解析、键解析和成功状态码策略，也会等待异步 Servlet 请求真实完成。缺少键返回 `400`；处理中与已完成请求都返回 `409`，并通过 `Idempotency-Status` 区分。当前实现不缓存或重放历史响应体。

## 限流、SingleFlight 和 Deadline

令牌桶限流配置示例：

```yaml
muskit:
  resilience:
    rate-limit-enabled: true
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
