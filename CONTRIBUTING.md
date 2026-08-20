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

新增并发额度、限流、锁、幂等、Inbox、Outbox 或缓存存储实现时，应首先复用 `muskit-test-support` 中的契约测试。实现不得通过弱化语义的方式让测试通过，也不得在后端故障时隐式回退为本地实现。

契约基类、适用范围和接入示例见 [`docs/testing/provider-contracts.md`](docs/testing/provider-contracts.md)。基础契约只验证 SPI 明确承诺的语义；跨实例协调和 fencing 等增强保证使用独立契约，Provider 不得声明自己不具备的能力。
