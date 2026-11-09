# Commercial Contract Architecture

ContractReference 标识原始商业修订和已编译条款，不拥有商业状态、服务调用或交易规则。

依赖仅为框架的 commons-error 与 commons-json；产品与收单 app/domain 可以直接消费该纯契约。
此模块与 pay-contracts-events 平级，不依赖任何 Pay client/domain，也不改变事件制品的职责约束。
本次为源码归属迁移，字段、校验顺序及失败语义保持不变，未执行构建或测试。
