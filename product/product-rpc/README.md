# Product RPC

本模块由 product 服务维护，包含本服务的 [Protobuf 契约](src/main/proto/product.proto)、`ProductRpcMapper` MapStruct 消息映射和 `ProductRpcOperations` 类型化方法定义。业务 client/domain 不依赖传输协议。

方法：`Authorize`、`Detail`。

提供方 adapter 注册本模块的生成服务，消费方 infrastructure 依赖本模块调用。字段号由本服务维护，删除字段必须 reserved；普通字段使用 MapStruct 生成，oneof/集合等协议边界保留显式转换；新增或调整消息须同步本模块映射和测试。通用 channel/JWT 支持见 [RPC 规范](../../design/RPC.md)。
