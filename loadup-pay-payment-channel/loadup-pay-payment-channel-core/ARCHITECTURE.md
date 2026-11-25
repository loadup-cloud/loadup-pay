# Payment Channel Core Architecture

## 路由

```text
Facade -> ChannelStrategyRegistry<ChannelProvider> -> 冻结 channel 的 Provider
```

注册表在构造时复制 Spring 收集的策略，按 `Channel` 建立不可变映射。重复渠道注册立即失败；注册后不随原集合变化。
支付、通知和账单 Facade 共用同一注册规则，但使用各自的策略类型。

## 统一核验

- 路由前校验本地事务、租户、binding revision 和 account scope。
- `submit` 校验结果身份、金额、动作渠道/方式和有效期。
- 查询/关闭/退款校验原身份、原金额和退款自然键。
- 恢复只接受 `QUERY_RESPONSE` 或符合协议的 `SUBMIT_RESPONSE`，不把重提受理当退款成功。

## 恢复并发

`ChannelRecoveryConcurrency` 为每个 `Channel` 取一个固定命名的 Resilience4j Bulkhead 实例：
`payment-channel-recovery-<channel>`。首提 `submit` / `submitRefund` 不进入该预算，避免在持久化首提权之后新增拒绝点。

## 动作快照

`RegistryChannelActionCodec` 只负责注册表分发与公共约束：kind/actionType 唯一、8 KiB 上限、SHA-256 摘要、未知 kind 拒绝。
厂商 binder 通过 `ChannelActionTypeCodec` 提供严格 JSON 和记录校验，core 不引用具体动作类型。
新增动作类型需同步 API 的 sealed 层次、对应 binder codec 和消费应用的 DTO 投影，但不需要修改 core 的 switch。
