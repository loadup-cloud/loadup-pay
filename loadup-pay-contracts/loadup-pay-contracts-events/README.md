# Pay 集成事件

本工件提供 acquire/payment 发布的跨服务事实事件与公开生命周期枚举，不包含请求响应、领域聚合或服务实现。

- `event.acquire`：订单快照与订单状态。
- `event.payment`：付款/退款快照、结果、商户退款及订单退款汇总事件，以及付款/退款状态。
- 发布方与消费方依赖同一版本；消费端校验来源、事件身份、来源版本及金额约束，不能用到达顺序覆盖较新的事实。

请求响应归提供方 client，商业合约引用归 commercial；依赖及转换规则见 [契约架构](../ARCHITECTURE.md)。事件消费与 receipt/投影更新的事务边界见各服务架构文档。

## 事实与交付

BusinessEventTypes 定义付款终态/快照、退款结果/快照/进展、订单退款汇总和订单快照的 canonical 类型。BusinessFact 保持业务身份、原聚合版本、时间和不可变 data，不含目的地或 deliveryId；每目标调用 delivery 生成 EventEnvelope。producer 声明不是认证身份，消费方必须保留实际服务身份/权限、本地原绑定、证据和幂等门禁。

FrozenEventTypes 仅允许各 DTO/入口声明的原 v1 冻结标签，不改写旧 payload/header/digest；DTO 内保留的 REALTIME_TYPE/FEE_TYPE/TYPE 是旧交付标签，不用于新事实生产。不可将 deliveryId 当业务事实或资金凭证。完整映射与读取退役条件见[阶段 3 记录](../../design/PHASE3_IMPLEMENTATION.md)。仅源码同步，未构建或测试。

## 自包含财务证据

PaymentFinancialEvidence 冻结原付款绑定、活动尝试、终态证据、版本、原金额及发起/成功时间、永久不收款围栏；RefundFinancialEvidence 引用完整原付款并补齐退款、累计、容量版本和退款时间。PaymentOutcomeEventDTO/RefundOutcomeEventDTO 与 proof 逐字段一致。

新生产使用 pay.payment.financial-outcome.v1 / pay.payment.refund-financial-outcome.v1（schema=1），必须有 proof。旧 canonical/限定目标类型仅接受原 null-proof 形态，旧 JSON 缺字段按 null 读取且 null 不序列化；新类型缺证据不得降级。业务身份不代替实际服务认证与权限。完整消费、摘要及保留最新累计查询见[财务证据记录](../../design/PHASE3_FINANCIAL_EVIDENCE.md)。仅源码调整，未构建或测试。
