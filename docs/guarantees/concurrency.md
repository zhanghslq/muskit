# 并发控制语义

## 控制对象

并发控制限制的是同时占用某个资源的调用数量，并不限制单位时间请求数量。限流属于单独能力，两者不能互相替代。

虚拟线程降低线程创建和阻塞成本，但不会增加数据库连接池、HTTP 连接池或第三方接口容量。因此本模块直接管理 Permit，不通过线程池大小间接表达资源容量。

## 本地实现

`LocalConcurrencyLimiter` 的保证范围是单个 JVM 进程：

- GLOBAL 策略按策略名称共享一个槽位。
- KEY 策略按策略名称和业务 Key 分别创建槽位。
- 等待者和额度持有者都会持有槽位引用。
- 最后一个调用释放引用后自动清理槽位，避免业务 Key 长期累积。
- Permit 重复关闭不会重复释放 Semaphore。

本地实现不提供跨实例并发上限。需要跨实例语义时必须显式使用 `muskit-concurrency-redis-adapter`，不能依赖自动降级。

## Redis 分布式实现

`RedisConcurrencyLimiter` 使用 Redis 服务端时间和原子 Lua 脚本维护带到期时间的额度令牌集合：

- 多个应用实例共享策略名称和业务键对应的最大并发数。
- 每次获取生成独立令牌，不依赖线程 ID，因此可以在异步完成线程释放。
- 正常执行期间自动续期；实例失联后令牌在租约到期时被清理。
- 原始业务键只以 SHA-256 摘要进入 Redis Key。
- Redis 访问失败会抛出 `ConcurrencyBackendException`，不会切换到本地 Provider。
- 显式配置 `provider=redis` 但缺少 `RedissonClient` 时应用启动失败。

当前 Redis 实现不提供公平排队。Redis 策略配置 `fair=true` 会明确抛出异常，避免把非公平实现伪装成公平语义。续期失败会输出只含策略名称和异常类型的 WARN；此时令牌可能在租约到期后被其他实例接管，业务应把 Redis 可用性作为分布式并发保证的前提。

## 方法执行范围

- 同步方法：进入方法前获取 Permit，方法返回或抛出异常后释放。
- CompletionStage：原方法返回后继续持有 Permit，在异步结果成功、失败或取消完成时释放。
- Reactor Publisher：当前版本不支持，不能将注解直接用于返回 Mono 或 Flux 的方法。

## Spring AOP 边界

`@ConcurrencyGuard` 基于 Spring AOP：

- 同一个 Bean 内部的自调用不会经过代理。
- private 方法不会被拦截。
- 默认切面顺序位于常规事务切面外层，使等待 Permit 时不占用数据库事务。
- 可以通过 `muskit.concurrency.order` 调整切面顺序，但应用需要自行理解事务和其他切面的嵌套关系。
