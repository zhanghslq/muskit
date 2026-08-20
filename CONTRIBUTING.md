# 参与贡献

感谢参与 Muskit 开发。

## 本地构建

```shell
./mvnw clean verify
```

提交代码前需要确保全量测试通过。涉及 Redis、数据库或消息系统的模块应使用 Testcontainers 提供可重复的集成测试。

## 代码约定

- Java 代码以 Java 21 为基线，不引入 preview API。
- 每个方法添加中文 Javadoc。
- 复杂状态机、并发和事务逻辑添加中文注释。
- 新建类添加 `@author zhs` 和创建日期对应的 `@since`。
- 新增或修改行为时同步增加单元测试、契约测试或集成测试。
- core 模块不得引入 Spring Boot 或具体基础设施客户端。

## 新增 Provider

新增 `ConcurrencyLimiter`、锁或幂等存储实现时，应首先复用 `muskit-test-support` 中的契约测试。实现不得通过弱化语义的方式让测试通过，也不得在后端故障时隐式回退为本地实现。
