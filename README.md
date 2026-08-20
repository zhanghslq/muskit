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
| `muskit-test-support` | 基于虚拟线程的并发测试辅助工具 |

## 引入依赖

应用项目可以先导入 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.muskit</groupId>
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
        <groupId>io.github.muskit</groupId>
        <artifactId>muskit-spring-boot-starter-context</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.muskit</groupId>
        <artifactId>muskit-spring-boot-starter-concurrency</artifactId>
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

## 后续路线

- `0.2.x`：分布式锁 SPI、Redisson adapter、可观测性
- `0.3.x`：幂等状态机、JDBC/Redis 存储、Kafka 消费适配
- `0.4.x`：RabbitMQ、HTTP 幂等、分布式并发控制
- `0.5.x`：限流、SingleFlight、Deadline

## 发布前事项

当前使用的 Maven 坐标为 `io.github.muskit`。正式发布 Maven Central 前，需要确认该 GitHub 组织或域名所有权；如果实际仓库归属不同，应在第一个公开版本前统一修改 groupId、Java 包名和项目 URL。

## License

Muskit 使用 Apache License 2.0。
