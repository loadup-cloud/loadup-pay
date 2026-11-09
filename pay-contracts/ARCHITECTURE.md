# 契约所有权与转换位置

`*-client` 是提供方公开契约，不包含领域聚合和实现依赖。对外枚举放在本服务 client，
本服务 domain 可以依赖这些纯契约类型；client 不反向依赖 domain。

消费方应用端口使用自己的模型。基础设施 HTTP 适配器通过构造器注入 `RemoteContractConverter`，
将本地请求映射为提供方请求，将远端响应映射为本地结果。HTTP 认证、事务外调用、错误码处理与重试策略仍由原有适配器负责。
分页转换保留 result、pageInfo 和行顺序；付款交互及动作的 sealed 子类型显式配置 MapStruct 子类映射。

跨服务事件是独立事实契约，按发布方组织。公共生命周期枚举是事件格式的一部分，可以被发布方及消费方使用；
业务内部的恢复状态、执行状态等按本服务归属，不能因多个服务使用就迁到公共事件包。

Payment 的接受请求携带 Payment 自己的冻结条款表示，避免 `payment-client` 传递依赖 `productcenter-client`。
Acquire 维护本地订单准入和付款操作表示，避免其公开响应直接嵌入其他服务 Java DTO。
事件校验仍检查事件来源、身份、版本和金额不变量；本次重构没有增加历史格式分支或兼容转发。

商业合约引用归 `pay-contracts-commercial` 的 `io.github.loadup.contracts.commercial.ContractReference`。
渠道词汇与固定 CNY 金额校验归 framework 的 payment-channel-contracts；事件保持独立于渠道操作 API。
