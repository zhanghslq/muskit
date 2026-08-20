# Muskit Agent 开发指南

本文件适用于仓库根目录及全部子目录。自动化开发工具在修改代码前应先阅读本文件，并同时遵守目标目录中更具体的 `AGENTS.md`（如果未来新增）。

## 项目目标

Muskit 是面向 Java 21 及以上版本的通用工程能力组件库。各项能力以独立模块提供，并通过 Spring Boot Starter 实现按需引入。目前优先保证清晰的模块边界、可替换的 SPI、明确的失败语义和可重复验证的测试。

项目基线：

- Java 21，不使用 preview API。
- Spring Boot 4.x，以根 `pom.xml` 中的版本为准。
- Maven 多模块工程，始终优先使用 Maven Wrapper。
- groupId 为 `io.github.zhanghslq`，版本由根项目统一管理。
- Java 基础包名为 `io.github.zhanghslq.muskit`。

## 开始修改前

1. 阅读根目录 `README.md`、`CONTRIBUTING.md` 和 `docs/architecture/module-boundaries.md`。
2. 使用 `rg` 或 `rg --files` 查找已有实现和测试，优先扩展已有抽象，避免创建功能重复的类型。
3. 检查当前工作区状态，保留用户已有修改，不覆盖、不回滚与当前任务无关的内容。
4. 只修改完成当前任务所需的模块；不要顺带重构无关代码或升级无关依赖。
5. 如果任务依赖本项目之外的代码，可以检查本项目同级目录中的项目；同级目录不存在时，将其视为外部依赖，不虚构其接口或实现。

## 模块和依赖边界

依赖方向必须保持为：

```text
starter -> autoconfigure -> core
   |              |
   +--> adapter --+
```

- `core`：公共注解、值对象、算法、状态机和 SPI。尽量只依赖 JDK，不依赖 Spring Boot 或具体中间件客户端。
- `adapter`：Redis、JDBC、Kafka、RabbitMQ 等具体技术实现。不同 adapter 之间禁止直接依赖。
- `autoconfigure`：Spring Boot 配置属性、条件装配和 AOP，只负责组装，不承载核心算法。
- `starter`：只提供经过验证的传递依赖，不放业务实现。
- `integration`：确实需要组合多个能力时使用，禁止通过反向依赖或循环依赖完成组合。
- `muskit-test-support`：公共测试工具和 Provider 契约测试，不得成为生产代码的运行时依赖。
- `examples`：演示公共 API 的最小用法，不作为其他模块的依赖。

新增可发布模块时必须同步完成：

1. 加入根 `pom.xml` 的 `<modules>`。
2. 加入 `muskit-bom/pom.xml` 的 `<dependencyManagement>`。
3. 在 `README.md` 中说明用途、依赖坐标和最小配置。
4. 为模块增加相应测试；涉及基础设施时优先使用 Testcontainers。

## Java 编码约定

- 每个新增或修改的方法、构造方法都必须添加中文 Javadoc，测试方法也不例外。
- 每个新建类、接口、枚举、注解和记录类型都必须包含：

```java
/**
 * 类型职责说明。
 *
 * @author zhs
 * @since YYYY-MM-DD
 */
```

- `@since` 使用创建当天日期，格式为 `yyyy-MM-dd`；不要因普通修改更新已有类型的创建日期。
- 复杂的并发、事务、状态转换、资源释放和异常恢复逻辑必须添加中文行内注释，解释设计原因和边界条件。
- 公共 API 优先使用不可变对象；配置中的时间统一使用 `Duration`。
- 资源句柄实现 `AutoCloseable`，`close()` 必须幂等。
- 捕获 `InterruptedException` 后必须恢复线程中断标记，除非方法继续向上抛出原异常。
- 不在异常消息、日志、指标标签、Actuator 输出或 `toString()` 中暴露消息 ID、租户 ID、锁 Key、幂等 Key 等敏感或高基数值。
- 不使用 Lombok 隐藏公共 API 或对象生命周期；保持发布产物的源码可读性。
- 不使用未经说明的静默降级。Redis、数据库或消息中间件不可用时，不得自动切换为本地实现。

## Spring Boot 自动配置约定

- 使用 `@AutoConfiguration` 和 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 禁止依赖组件扫描发现 Muskit 内部 Bean。
- 默认 Bean 使用 `@ConditionalOnMissingBean`，允许使用者显式替换。
- 第三方集成使用可选依赖，并通过 `@ConditionalOnClass` 等条件保护。
- 每组配置使用独立前缀，建议格式为 `muskit.<module>`。
- 配置类使用类型安全的 `@ConfigurationProperties`，确保生成 `spring-configuration-metadata.json`。
- 自动配置至少测试默认启用、用户覆盖或核心行为、显式禁用三个场景。

## 并发与上下文代码约定

- 不假设虚拟线程天然消除资源瓶颈；外部连接、线程池和业务资源仍需显式限制并发。
- `ThreadLocal` 状态必须在 `finally` 或作用域句柄中恢复，测试线程复用时不存在上下文泄漏。
- 嵌套上下文必须恢复上一个值，而不是简单清空。
- 异步返回值持有的许可或锁，应在实际异步任务完成后释放，不能在方法返回时提前释放。
- 并发测试必须有超时边界，避免失败时永久阻塞；优先复用 `muskit-test-support`。
- SPI 实现应通过同一套契约测试，不能通过弱化语义使特定 Provider 通过测试。

## 测试和验证

新增或修改行为时，必须同步新增或调整单元测试、契约测试或集成测试。测试应覆盖正常路径以及本次修改涉及的超时、拒绝、异常、清理或并发边界。

优先执行受影响模块及其依赖：

```shell
./mvnw -B -ntp -pl <module> -am test
```

提交前执行全量验证：

```shell
./mvnw -B -ntp clean verify
```

涉及发布配置、公共 API 或 Javadoc 时额外执行：

```shell
./mvnw -B -ntp -Prelease "-Dgpg.skip=true" clean verify
```

Windows PowerShell 使用对应的 `mvnw.cmd`。不得仅用跳过测试的构建作为完成依据。

## 完成标准

一次修改只有同时满足以下条件才算完成：

- 实现符合模块边界，没有新增循环依赖或不必要的传递依赖。
- 新增代码符合中文 Javadoc、作者和日期要求。
- 行为变化有自动化测试保护，相关测试和全量构建通过。
- 配置、公共 API 或使用方式发生变化时，README 和相关文档已经同步。
- 最终说明列出修改内容、验证命令和结果，以及仍未覆盖的限制；不得声称未执行的测试已经通过。
