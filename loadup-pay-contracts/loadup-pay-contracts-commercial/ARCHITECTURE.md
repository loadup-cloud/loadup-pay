# Commercial Contract Architecture

ContractReference 标识原始商业修订和已编译条款，不拥有商业状态、服务调用或交易规则。

依赖仅为框架的 commons-error 与 commons-json；产品与收单 app/domain 可以直接消费该纯契约。
此模块与 pay-contracts-events 平级，不依赖任何 Pay client/domain，也不改变事件制品的职责约束。
字段、校验顺序和失败语义由本工件统一维护；消费方映射及真实调用需独立验收。
