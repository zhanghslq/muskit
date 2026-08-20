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

Transactional Outbox 同样遵守该方向：`muskit-outbox-core` 只定义事件、事务存储和发布 SPI；JDBC 与 Kafka 分别位于独立 adapter；自动配置只组装两者，Starter 仅提供经过验证的传递依赖。业务代码只能通过 `OutboxService` 写入事件，不直接依赖后台发布实现。

新增治理能力继续按相同边界拆分：

- `muskit-observation-core` 只定义稳定指标和观测 SPI，Micrometer 与 Actuator 位于 adapter/autoconfigure。
- `muskit-lifecycle-core`、`muskit-executor-core` 不依赖 Servlet、WebFlux 或 Spring Boot；两种 Web 技术栈的摘流和 Bean 生命周期只在 autoconfigure。
- `muskit-context-core` 不依赖 Reactor；Reactor Context API 位于独立 adapter，并由专用 Starter 按需引入。
- `muskit-inbox-core`、`muskit-cache-core`、`muskit-audit-core` 只定义状态机、算法和 SPI，JDBC/Redis/Micrometer 分别由 adapter 实现。
- `muskit-client-core` 只处理请求头值的安全编码、Deadline 和上下文作用域，Spring RestClient/Servlet 位于 Spring adapter。
- `muskit-state-core` 只提供无副作用迁移判定与乐观锁仓储 SPI，业务自行实现实体仓储。

## 自动配置约束

- 使用 `@AutoConfiguration` 和 `AutoConfiguration.imports`。
- 禁止通过组件扫描发现 Muskit 内部 Bean。
- 用户声明同类型 Bean 时，默认实现必须退让。
- 第三方客户端必须是可选依赖，并使用 `@ConditionalOnClass` 保护。
- 自动配置只负责组装对象，核心算法不能写在配置类中。
- 不允许在 Redis 等后端不可用时静默切换到本地实现。

## 分布式正确性约束

- Redis 分布式限流使用服务端时间和原子脚本，不以应用实例时钟决定共享令牌状态；后端异常必须明确失败。
- HTTP 幂等结果回放设置响应体上限、Content-Type 策略和响应头白名单；不可回放的完成态不能重新执行业务。
- Retry 必须受当前 Deadline 剩余预算约束，不能通过继续重试突破调用链截止时间。
- Circuit Breaker 的异步结果在任务真实完成后记录，不能在返回 `CompletionStage` 时提前计为成功。
- Transactional Outbox 提供至少一次投递：事件写入必须参与业务事务，发布租约必须有期限，broker 确认后才能标记成功；消费者仍需幂等处理重复事件。
- fencing token 只能由分布式 Provider 生成；要求 fencing 的请求禁止降级为本地锁，业务下游必须以 token 做条件更新。
- Inbox 和 Outbox 达到最大尝试次数后进入显式 DEAD，人工回放前不得假装成功；同一 Outbox 分区的 DEAD 会阻塞后续事件以保持顺序。
- 缓存后端失败模式必须显式配置，失效操作不允许绕过后端；缓存 Key 不以明文进入 Redis。
- 客户端传播只接受配置白名单，请求 Deadline 只能收紧且必须限制远端给出的最大预算。
- 持久化状态机只通过 Provider 的原子 compare-and-set 提交，冲突后必须重新读取，不能在过期快照上盲目重试。

## 公共 API

- 公共 API 以 Java 21 为基线，但不使用 preview API。
- 配置中的时间统一使用 `Duration`。
- 资源句柄实现 `AutoCloseable`，释放操作必须幂等。
- 异常类型携带低基数策略信息，不保存敏感业务 Key。
- `0.x` 阶段允许调整 API，`1.0.0` 后遵守语义化版本。

## 可观测性约束

模块接入 Micrometer 时，指标标签只能使用 policy、provider、operation、outcome 等低基数字段。tenantId、锁 Key、消息 ID 和幂等 Key 不允许作为指标标签，也不能由 Actuator 端点直接展示。所有模块通过 `MuskitObservationRegistry` 接入统一指标目录，具体 Micrometer 依赖只存在于适配层。
