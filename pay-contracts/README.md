# Pay 跨服务契约

请求、响应、查询和错误码由提供方的 `*-client` 拥有。消费方在自身模型中表达所需数据，
仅 infrastructure 的 HTTP 适配器依赖提供方 client，并通过 Spring 管理的 MapStruct `RemoteContractConverter` 转换。

| 位置 | 所有权 |
|---|---|
| `payment/payment-client` | 付款命令、查询、响应、错误码，以及付款接受时冻结的条款快照 |
| `productcenter/productcenter-client` | 产品条款与准入接口的契约 |
| 各服务 `client.enums` | 需要对外暴露的状态和参数枚举；domain 可使用这些纯类型 |
| 消费方 `client.dto.<provider>` | 消费方公开接口自身需要的表示，不向上游暴露远端 Java 类型 |
| 消费方 `app.model.<provider>` | 应用端口、持久化快照、业务编排所需的本地模型 |
| `pay-contracts-commercial` | 产品与收单共享的不可变商业合约引用 |
| `pay-contracts-events` | 按发布方组织的集成事件和事件生命周期枚举 |

`pay-contracts-events` 只保留七个事件记录及三个公开事件状态枚举，包名为
`io.github.loadup.contracts.event.acquire` 和 `io.github.loadup.contracts.event.payment`。
命令、查询、付款交互结构和产品 DTO 不放在事件工件中。

依赖规则：

- client 不依赖 domain、应用实现或其他 Pay 服务 client。
- app/domain 不导入其他 Pay 服务 client；消费方基础设施负责处理远端协议。
- 事件工件不依赖服务的 client/domain，也不依赖渠道 API；渠道枚举与金额策略直接引用轻量 payment-channel-contracts。
- 使用同一个 reactor 发布版本；本轮直接全量迁移包名、类型及所有调用方，不保留兼容别名或历史适配层。
- 结构门禁位于 `scripts/tests/test_contract_ownership.py`，验证依赖方向、事件工件职责及 Maven 模块无环。

本地模型与远端模型的字段可以独立演进；调整远端协议时，更新对应基础设施 Converter 以及请求/响应测试。
Converter 保留金额、时间、状态和摘要字段，不承担交易决策、重试或状态迁移。
