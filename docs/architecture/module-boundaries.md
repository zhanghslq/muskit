# 模块边界

## 依赖方向

```text
starter ──> autoconfigure ──> core
   │               │
   └──> adapter ───┘
```

依赖只能从上向下：

- core 保存公共注解、值对象、状态机和 SPI，尽量只使用 JDK API。
- adapter 实现 Redis、JDBC、Kafka、RabbitMQ 等技术相关能力。
- autoconfigure 负责 Spring Boot 条件装配、配置属性和 AOP。
- starter 不包含业务实现，只提供一组经过验证的传递依赖。

adapter 之间禁止直接依赖。跨能力组合通过独立 integration 模块实现，避免 context、lock、idempotency 和 messaging 形成循环依赖。

## 自动配置约束

- 使用 `@AutoConfiguration` 和 `AutoConfiguration.imports`。
- 禁止通过组件扫描发现 Muskit 内部 Bean。
- 用户声明同类型 Bean 时，默认实现必须退让。
- 第三方客户端必须是可选依赖，并使用 `@ConditionalOnClass` 保护。
- 自动配置只负责组装对象，核心算法不能写在配置类中。
- 不允许在 Redis 等后端不可用时静默切换到本地实现。

## 公共 API

- 公共 API 以 Java 21 为基线，但不使用 preview API。
- 配置中的时间统一使用 `Duration`。
- 资源句柄实现 `AutoCloseable`，释放操作必须幂等。
- 异常类型携带低基数策略信息，不保存敏感业务 Key。
- `0.x` 阶段允许调整 API，`1.0.0` 后遵守语义化版本。

## 可观测性约束

后续模块接入 Micrometer 时，指标标签只能使用 policy、provider、outcome 等低基数字段。tenantId、锁 Key、消息 ID 和幂等 Key 不允许作为指标标签，也不能由 Actuator 端点直接展示。

