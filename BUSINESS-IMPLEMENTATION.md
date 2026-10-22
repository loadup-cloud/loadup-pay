# 全部业务实现进度

目标保持为完整收单业务实现，不以当前批次代替最终范围。最新范围：付款码（商户扫码枪输入）、JSAPI、H5；纯 API DirectPay/create→startPayment 与可选托管收银台。直连/服务商、商户/子商户均需支持。Native 二维码及原生 APP 支付不在范围。无消费者用户/余额/充值/提现/转账。

用户要求暂不运行 IDEA 检查、构建或测试。以下“源码已补”不等于验证或部署通过；完整完成审计必须保留这一未验证边界。不要启动全量构建、改成假渠道成功、用公开 evidenceId 直接记账，或在数据库锁内跨服务调用。

| 编号 | 业务工作 | 当前权威状态/后续动作 |
|------|----------|---------------------|
| B01 | 商户资格/授权 | customer 资格/授权 app/infra/adapter 已有；主档维护继续复用框架商户能力 |
| B02 | 产品合约/准入/限额发布 | 原准入、初始策略 Outbox/ACK 已有；多 revision/多产品共享策略与渠道引用开通校验待补；payment 绑定发布/暂停/撤销与不可变身份版本源码已补 |
| B03 | 订单创单/Checkout/占用/结果/关闭/延期 | 当前 COLA 源码已补；DirectPay 便利受理源码已补；自动恢复/到期任务及结果事件待补 |
| B04 | 费用 | 本批已将整单费用确认接入 A3 的同一事务；退款累计基数、退费调整仍待补 |
| B05 | 商户限额 | 激活、预占、执行许可、显式过期已有；本批新增 confirm/release、权威查证和持久回执；退款恢复、安全换 attempt、过期扫描及历史仍待补 |
| B06 | 付款码/JSAPI 输入准备 | prepare 端点、KMS 密文、HMAC 指纹/轮换、到期检查、单 attempt 绑定及 acquire 接入源码已补；密文清理/后台恢复待补 |
| B07 | payment 执行主链 | 受理/查询/封禁/安全关闭已有；绑定管理/不可变账户版本/内部选路器源码已补；首笔 attempt/路由冻结、额度 Reserve/Begin/Verify、默认 risk/Y2 CAS 与未提交关单源码已补；真实发送/动作/结果/交易号 claim/查单/关闭收敛、版本化 routingProfileRef 和安全换 attempt 仍待实现 |
| B08 | 渠道 | 支付宝/微信付款码、JSAPI/H5：直连/服务商真实协议适配待实现；银联产品/机构资料仍未指定，不能凭猜测宣称可用 |
| B09 | 退款 | 纯领域及契约已有；独立 request/plan/refund、FIFO 固定分配、金额预占、执行/未知查证、结果/汇总待实现 |
| B10 | cashier/纯 API | 摘要/能力/会话已有；纯 API start/DirectPay 受理编排已补；托管页面付款交互/撤销、真实渠道动作仍待补，托管会话不是纯 API 的前置 |
| B11 | 异步结果/通知 | 事务事件、可靠投递/重放、签名/ACK/重试、回跳白名单与凭证轮换待闭环 |
| B12 | realtime | client/domain 已有；独立投影存储、版本/幂等、商户范围查询待实现 |
| B13 | 风控 | client/domain SPI 已有；默认 DEFAULT_ALLOW 实现及 Y2 前咨询源码已补；结果异步确认和外部接入策略待接入 |
| B14 | reconciliation | 后置服务尚未落地；文件批次/解析/核对/差错复核，不直接修改交易资金表 |
| B15 | 验证/运行 | 用户暂停验证；最新源码未编译/测试，新增业务 DDL/镜像/部署未执行 |

当前继续点：完成原 routingProfileRef 资源验证和真实渠道发送/动作/结果/交易号 claim，接通接受后自动调度/查证及退款权威事实，再接 limitcenter 退款恢复、异步任务/通知和查询/对账。新权威付款额度查询位于 `/api/payment/facts/payment`，只供指定 limitcenter SERVICE+pay:payment:limit-facts；限额确认/释放入口 `/api/limit/settlement/confirm|release` 仅允许指定 payment SERVICE。新 release 返回 LimitReleaseDTO，reservation 可空；不要为先释放后预占构造假 reservation。

所有未完成项保持待办；实现后逐项更新此表、ROADMAP 和模块说明，不以测试源码/未运行计划当作完成证据。
