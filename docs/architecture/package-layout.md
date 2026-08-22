# 子模块包结构

Muskit 的 Maven 模块负责隔离可选依赖和发布边界，模块内部的 Java 包继续按职责拆分，避免把注解、模型、SPI、异常、执行器和框架适配放入同一个包。

## Core 模块

Core 模块以 `io.github.zhanghslq.muskit.<能力>` 为根包，并按下面的稳定职责组织：

| 子包 | 职责 |
| --- | --- |
| `annotation` | 面向业务方法或类型的公共注解 |
| `model` | 不可变请求、结果、策略、状态和值对象 |
| `spi` | 可由应用或 Adapter 替换的扩展接口 |
| `exception` | 对外公开且具有明确失败语义的异常 |
| `service` | 编排状态机或 SPI 的程序化入口 |
| `context` | 具有明确作用域和恢复语义的上下文 |
| `local` | Core 中不依赖外部设施的本地实现 |

只包含一两个紧密协作类型时不强制创建空洞子包。模块根包不再同时承载三种以上职责；新增类型优先进入已有职责包。

## Adapter 模块

Adapter 先以具体技术命名，例如 `redis`、`jdbc`、`kafka`、`rabbit`、`reactor` 或 `spring`。当同一个 Adapter 同时包含多个独立适配点时，再在技术包下按功能细分，禁止 Adapter 之间通过包引用形成耦合。

## Autoconfigure 模块

自动配置的根包只保留共享配置属性和模块级装配。多能力模块按业务能力拆分，例如：

```text
resilience.autoconfigure
├── ratelimit
├── retry
└── circuitbreaker
```

Web 技术栈按 `servlet`、`reactive` 区分，具体 Provider 装配按 `redis`、`jdbc`、`kafka` 等区分。移动自动配置类时必须同步维护 `AutoConfiguration.imports`，不能依赖组件扫描兜底。

## 测试代码

测试包跟随被测生产类型的功能包。跨 Provider 的公共契约继续位于 `io.github.zhanghslq.muskit.test.<能力>`，示例代码按演示能力拆包，不作为其他模块的依赖。
