# 幂等状态语义

## 状态机

所有 Provider 共享以下状态语义：

```text
不存在 --tryStart--> PROCESSING --complete--> COMPLETED
                         |
                         +--release/超时--> 不存在

COMPLETED --保留期到期--> 不存在
```

- `tryStart` 必须原子创建 PROCESSING 记录，并返回唯一所有权令牌。
- 已存在未超时的 PROCESSING 返回 `IN_PROGRESS`。
- 已存在未过期的 COMPLETED 返回 `COMPLETED`。
- 只有持有当前所有权令牌的调用可以 complete 或 release；所有权已丢失时抛出 `IdempotencyOwnershipLostException`。
- 业务成功才提交 COMPLETED；业务失败释放 PROCESSING，使重试可以重新执行。
- `CompletionStage` 在真实完成信号到达前一直保持 PROCESSING。

## Provider 保证

Redis Provider 用单条 Lua 脚本完成获取、提交和释放，业务键以 SHA-256 摘要构成 Redis Key。JDBC Provider 用 operation/key 唯一主键、条件更新和所有权令牌实现相同状态机。JDBC 自动建表适合本地开发，生产环境建议由数据库迁移工具管理结构。

Provider 不会相互降级。选择 Redis 但没有 `RedissonClient`，或选择 JDBC 但没有 `JdbcOperations`，应用会启动失败。后端运行时异常转换为不包含业务键和所有权令牌的稳定异常。

## 入口适配

- Kafka 以 topic、partition、offset 作为稳定键。已完成记录跳过监听器；处理中异常交给容器重试策略。
- RabbitMQ 默认要求 messageId，允许自定义键解析器。处理中异常是否重回队列由容器配置决定；批量监听器当前不支持。
- HTTP Filter 默认要求 `Idempotency-Key`，同步和异步 Servlet 请求都在真实结束后完成状态。当前只防止重复执行，不缓存或重放首次 HTTP 响应。

这些适配都依赖消息生产者、客户端或上游网关提供稳定且在保留期内唯一的幂等键。业务键复用会被正确视为重复请求，而不是新业务。
