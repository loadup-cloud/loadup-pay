# Merchant Center Service 架构

本模块把 Merchant Center 的 domain、app 与 infrastructure 放在同一发布单元中，保持包级依赖边界。可复用 Java 契约由 [client](../loadup-pay-merchantcenter-client/ARCHITECTURE.md) 定义，HTTP 与安全装配由 [bootstrap](../loadup-pay-merchantcenter-bootstrap/ARCHITECTURE.md) 负责。

## 领域与应用

- `domain.model` 定义 `Merchant`、`MerchantRelationship`、`MerchantActionGrant`、状态和商户事实；领域对象表达商户资料、关系及授权状态。
- `app.service` 执行商户创建、更新、查询、状态变更、关系与授权命令；`CustomerService` 汇总资格校验，`MerchantIdentity` 负责资格判断所需身份事实。
- `app.security` 提供经过适配器认证的调用主体抽象。请求中的操作者字段不能替代 Resource Server 验证所得身份。

## 持久化与事务

`infrastructure.repository` 实现领域 Gateway，`dataobject`、`mapper`、`converter` 负责数据库记录和领域模型映射。商户主档、关系、动作授权及其 Flyway 迁移归本服务所有；迁移位于 `src/main/resources/db/migration/pay/merchantcenter`。GlobalUnique 与 ID/序列分配使用共享组件。

创建、授权、撤销和资格判断在同一数据库执行短事务；按商户主档行锁串行化授权变更与资格判断，数据库唯一约束兜底并发。资格检查同时核对有效商户关系及相应动作授权。撤销保留资源身份与版本历史，不能把授权自动迁移到新关系。

## 系统边界

商户合约数据归 Product Center，本模块不复制或直接访问合约表。Merchant Center 通过受控接口提供商户摘要和资格结果；调用凭证、HTTP 路由及服务间信任配置属于应用装配，详见 [bootstrap 架构](../loadup-pay-merchantcenter-bootstrap/ARCHITECTURE.md)。整体数据与身份边界见[Pay 总体架构](../../ARCHITECTURE.md)。
