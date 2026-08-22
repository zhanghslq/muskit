# Provider 契约测试

`muskit-test-support` 提供可被第三方 Provider 直接继承的 JUnit 5 契约测试。契约用于固定 Muskit SPI 的失败语义、所有权校验和跨实例可见性，避免不同后端实现出现行为漂移。

## 可用契约

| 契约基类 | 验证内容 |
| --- | --- |
| `DistributedLockProviderContract` | 跨实例互斥、业务键隔离、跨线程幂等释放、禁止隐式线程重入 |
| `FencedDistributedLockProviderContract` | 包含基础锁契约，并验证同一业务键的 fencing token 严格递增 |
| `IdempotencyStoreContract` | PROCESSING/COMPLETED 共享、失败释放、结果回放、续期、陈旧所有者拒绝 |
| `ConcurrencyLimiterContract` | 容量拒绝、按键隔离、跨线程幂等释放 |
| `DistributedConcurrencyLimiterContract` | 包含基础并发契约，并验证多个应用实例共享并发上限 |
| `RateLimiterContract` | 令牌桶容量拒绝、重试等待建议和按键隔离 |
| `DistributedRateLimiterContract` | 包含基础限流契约，并验证多个应用实例共享令牌桶 |
| `InboxStoreContract` | 成功去重、处理租约、延迟重试、租约接管、死信和人工回放 |
| `OutboxRepositoryContract` | 原子批量竞争、发布租约、延迟重试、陈旧所有者拒绝、死信和历史清理 |
| `CacheStoreContract` | 跨实例读写、空值占位、缓存名称隔离和删除 |

基础契约和增强契约必须按 Provider 声明的能力选择。例如本地 `Semaphore` 实现可以满足 `ConcurrencyLimiter` 的单进程语义，但不能继承 `DistributedConcurrencyLimiterContract`。要求 fencing 的锁 Provider 应继承 `FencedDistributedLockProviderContract`；不支持 fencing 的 Provider 必须对相应请求明确失败。

## Maven 依赖

Provider 测试模块通过测试范围引入契约：

```xml
<dependency>
    <groupId>io.github.zhanghslq</groupId>
    <artifactId>muskit-test-support</artifactId>
    <version>${muskit.version}</version>
    <scope>test</scope>
</dependency>
```

## 接入示例

下面的 Redis 并发 Provider 使用两个独立客户端验证跨实例协调。测试类必须负责创建和关闭 Provider 持有的线程、连接或容器资源。

```java
class CustomRedisConcurrencyLimiterTest extends DistributedConcurrencyLimiterContract {

    private ConcurrencyLimiter first;
    private ConcurrencyLimiter second;

    @BeforeEach
    void setUp() {
        first = createLimiter(firstRedisClient);
        second = createLimiter(secondRedisClient);
    }

    @Override
    protected ConcurrencyLimiter firstLimiter() {
        return first;
    }

    @Override
    protected ConcurrencyLimiter secondLimiter() {
        return second;
    }
}
```

示例只展示契约所需结构；仓库中的 Java 测试代码仍必须遵守中文 Javadoc、资源清理和超时边界要求。涉及 Redis、数据库或消息中间件时，应使用 Testcontainers 验证真实后端，不应只依赖 Mock 证明原子性。

## 时间和事务适配

Inbox 与 Outbox 契约通过 `...At(Instant now)` 工厂让 Provider 使用可控时钟测试租约边界。Outbox 的 `append(...)` 抽象方法用于由实现建立真实业务事务；契约不会绕过 Provider 对活动事务的要求。

可选统计方法返回负数时，契约跳过数值断言，但不会跳过核心状态转换。Provider 一旦返回非负统计值，就必须保证统计结果与状态机一致。

## API 兼容基线

当前 `0.1.0-SNAPSHOT` 尚无已发布的前一版本，暂时不能建立可信的二进制兼容比较基线。首次向中央仓库发布后，应以该正式版本作为基线，在发布构建中加入公共 API 二进制兼容检查；在此之前通过 Provider 契约、完整 Javadoc 和 Release Profile 校验稳定扩展点，避免用空基线制造虚假的兼容承诺。
