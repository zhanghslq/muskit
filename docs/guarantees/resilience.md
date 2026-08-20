# 限流、SingleFlight 和 Deadline 语义

## 本地令牌桶

`LocalTokenBucketRateLimiter` 使用单调纳秒时钟连续补充令牌：

- 初始令牌数等于 capacity。
- refillTokens 在 refillPeriod 内按比例连续恢复，不依赖整秒窗口。
- GLOBAL 按策略共享桶，KEY 按策略和业务键隔离。
- 最大桶数量提供硬内存保护，空闲桶超过保留时间后清理。
- 判定只消耗入口令牌，不限制同时执行数量；并发上限应使用 concurrency 模块。

本地实现只保证单 JVM 速率。集群限流需要应用替换 `RateLimiter` SPI，不能把每实例本地限流误认为全局限流。

## SingleFlight

`SingleFlight` 只合并“当前正在执行”的同键调用，不缓存已经完成的结果。竞争胜出的调用执行 Supplier，其他调用共享其成功或失败结果。每个调用方拿到独立的依赖结果，取消自己的结果不会取消底层共享执行。共享任务完成后使用 key 和占位对象条件删除，旧任务不会误删后续同键新任务。

SingleFlight 不等于幂等：它只在一个 JVM 的并发时间窗口内合并调用，不记录历史成功状态，也不提供跨实例语义。

## Deadline

`DeadlineContext` 保存绝对截止时间：

- 嵌套作用域总是选择父子 Deadline 中更早者，子调用不能延长上游预算。
- `check()` 只做协作式到期检查，不会自动中断业务线程或取消外部请求。
- Scope 必须按嵌套顺序在创建线程关闭，重复关闭幂等。
- `wrap(Runnable/Callable)` 捕获当前 Deadline，在目标线程执行时与目标线程已有 Deadline 取更早者，并在结束后恢复。

调用数据库、HTTP 或消息客户端时，业务仍应把 `remaining()` 转换为对应客户端的超时设置。仅创建 Deadline 而不检查或不下传超时，不会自动停止阻塞操作。
