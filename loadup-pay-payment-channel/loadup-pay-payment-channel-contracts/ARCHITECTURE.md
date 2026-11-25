# Payment Channel Contracts Architecture

```text
commons-dto / commons-error
             ↑
payment-channel-contracts
             ↑
payment-channel-api / consumer contracts
```

基础词汇与金额约束独立于操作接口，避免跨服务事件为共享枚举而依赖完整渠道 API。
本制品不依赖 API、core、binder、数据库或消费应用。业务的严格正金额要求由具体请求继续校验；共享非负校验允许零值。
通用 Money 的多币种、有符号语义保持在 commons，固定 CNY 是本渠道收单契约的策略。
