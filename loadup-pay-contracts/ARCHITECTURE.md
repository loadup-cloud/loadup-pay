# 契约所有权与转换位置

当前关键请求、事实、回执及稳定业务键已登记在[阶段 0 核心契约](../design/PHASE0_FLOWS.md)。提供方 client 中的 HTTP 声明带有 Spring 传输依赖；“不包含实现依赖”不表示整个 client 是零技术依赖工件。阶段 3 已新增目标无关事实类型与限定冻结读取，目标标签仍作 Task 路由；完整付款证据路径已替代符合条件的回查，退款最新累计查询与旧冻结查证保留。

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

阶段 2 只拆分 productcenter 内部商业解析与编译，不改公开条款、AcquiringTermsDTO、compilerVersion/schemaVersion 或规范化哈希协议。内部领域片段不进入 CompiledAcquiringTerms；消费方 acquire/payment 的 DTO 与 RemoteContractConverter 保持原结构。固定输出及待运行回归见[阶段 2 记录](../design/PHASE2_IMPLEMENTATION.md)，未构建或测试。

阶段 3 新增 BusinessEventTypes、BusinessFact 和 FrozenEventTypes。业务事实不含目标/deliveryId；每目标投递仍使用原 EventEnvelope 字段，data 为原不可变 DTO。新增生产只写 canonical 类型；原冻结目标标签只在指定载荷/消费入口读取，既有 digest 不归一化。名称变化不增加金融证据，也不改变 JWT、安全范围或查证要求。首批类型/路由全表和待运行证据门禁见[阶段 3 记录](../design/PHASE3_IMPLEMENTATION.md)。

2026-11-16 补充 PaymentFinancialEvidence/RefundFinancialEvidence，DTO 外层与 proof 强绑定；完整证据使用独立新类型且不得缺 proof 降级。limitcenter-client 直接依赖 contracts-events；字段、依赖与保留查询见[财务证据记录](../design/PHASE3_FINANCIAL_EVIDENCE.md)。未构建或测试。
