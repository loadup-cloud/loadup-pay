# 长期 UNKNOWN 分级查证

更新：2026-10-27。对应 review R3 的源码切片；未编译、测试、部署或实际发送告警。

## 运营策略

RecoveryEscalationPolicy 只解释首次 UNKNOWN 年龄，付款与子退款共用。NORMAL 为正常观察、REVIEW 为待查证、URGENT 为紧急查证。
这些值不是付款/退款状态，不改变轮询、额度、门禁、attempt、渠道原请求号或提交权。达到阈值也不自动失败、关单、退款、释放额度或再次提交。

| 配置（loadup.modules.payment.recovery-escalation） | 环境变量                              | 默认             |
|----------------------------------------------------|---------------------------------------|------------------|
| review-after-seconds                               | PAYMENT_RECOVERY_REVIEW_AFTER_SECONDS | 900（15 分钟）   |
| urgent-after-seconds                               | PAYMENT_RECOVERY_URGENT_AFTER_SECONDS | 86400（24 小时） |

要求 1 <= review < urgent <= 31536000 秒；边界达到即升级，部分秒不提前进位，时钟回拨年龄下限为 0。
这是服务端运营关注阈值，和合约订单有效期/退款窗口、native task 重试次数分开。配置变更只影响后续查询/观测，无持久资金效果；不是已承诺的生产
SLA。

## 受控查证列表

POST /api/payment/operations/recovery/unknown/list，独立 PaymentUnknownRecoveryFacade。
只接受已验证 SERVICE、配置的 recovery-operations-service-id（默认 pay-ops）及 pay:payment:recovery:read 权限。
租户从已验证凭证取得，入参没有 tenantId 或任意数据库查询条件，商户接口不会获得该能力。响应 no-store。

请求结构示例：

```json
{
  "kind": "COLLECTION",
  "minimumLevel": "REVIEW",
  "afterFirstUnknownAt": null,
  "afterRecordId": null,
  "limit": 50
}
```

- kind=COLLECTION 每行是一笔 payment；kind=REFUND_REQUEST 每行是一个 UNKNOWN 子退款，不按申请去重。
- minimumLevel 包含该级及更高级；NORMAL 不加年龄谓词，查询全部当前 UNKNOWN；REVIEW/URGENT 按服务端阈值过滤。时钟回拨造成
  first_unknown_at 晚于当前时间的行仍在 NORMAL 可见、可继续分页，年龄按 0 计算。
- 返回原 recordId、subjectId、orderId、payeeMerchantId、CNY Money、首次时间、年龄/级别、rowVersion 及观察到的
  recoveryGeneration/recoveryCompleted。
- 退款 recordId 是子退款 ID，subjectId 是退款申请 ID；付款二者均为 payment ID。
- 缺失恢复游标时两个恢复元数据为 null，仍保留原 UNKNOWN 记录供定位。UNKNOWN 但 completed=true 也是待查证信息，均不授权重建任务。
- 不返回输入、渠道凭证、意图负载、原始渠道响应或 native claim token。

每页 1..100，数据库最多取 limit+1，不查询总数。排序为 first_unknown_at ASC、原记录 ID ASC；下一页使用响应
nextFirstUnknownAt/nextRecordId 原值，游标必须成对且为微秒时间。
最后一页 hasMore=false，两个下一页字段为 null。时间采用既有 BusinessTime 本地时区对外 OffsetDateTime，不强制 UTC。
查询用当前时点判断年龄，返回 observedAt 与当次阈值，跨页保持 kind/minimumLevel；每一页是独立 RR
快照，交易终结/配置变更/新记录可使队列变化，不是完整导出的固定快照。

实际 Reader 使用参数化 Spring JDBC 查询，
付款联 payment_recovery_cursor，子退款联原 refund_request/refund_recovery_cursor，所有表明确当前 tenant 和未删除条件。
缺失退款父数据/非法映射明确失败，不能隐藏该条而伪装完整结果；缺失游标允许显式 null。
实际 REQUIRES_NEW / readOnly / REPEATABLE_READ 内读取，无 FOR UPDATE、写入、跨服务 IO 或每行 native 查询；不调用任务、渠道或退款推进。
应用层拒绝越界、错误 kind、重复或乱序结果，避免错误 Reader 生成失真的分页结果。
分页限额不等于扫描成本有界；当前没有新增推测索引，实际计划、排序/快照成本和调用频率仍待运行验收。该列表不由 Collector 周期调用。

## 指标与规则源码

Collector 继续默认未启用，仅对配置租户范围采样。新增三个无身份标签的 Gauge（共同前缀 loadup.payment.recovery.inventory）：

| 后缀                  | 单位    | 含义               |
|-----------------------|---------|--------------------|
| .sample.interval      | seconds | 配置采样周期       |
| .unknown.review.after | seconds | 本进程待查证阈值   |
| .unknown.urgent.after | seconds | 本进程紧急查证阈值 |

[独立 Prometheus 规则](../deploy/payment-recovery-alerts.yml) 按现有 loadup-pay 抓取 job / payment application 编写：

- 完整样本 initialized=1、enabled=1、target UP，且 sample.age <= 2*interval+60 秒，才以 UNKNOWN 最老年龄判断关注级别。
- REVIEW 与 URGENT 规则互斥，持续 2 分钟触发；阈值来自实际进程指标，不另写死 15 分钟/24 小时。
- 启用但从未初始化或样本过旧，持续 5 分钟触发库存不可用告警；旧样本不会被当作当前资金告警或零库存。
- 不求和多个副本的重复库存；保留现有 job/instance/pod 与固定 kind，标签/描述不放租户、订单、付款/退款或账户身份。

规则采用 [Prometheus 告警规则](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/) 和
[显式向量匹配](https://prometheus.io/docs/prometheus/latest/querying/operators/)，指标命名遵循
[Micrometer 命名约定](https://docs.micrometer.io/micrometer/reference/concepts/naming.html)。实际 exporter
名称、标签与规则评估仍未运行确认。
规则文件是标准 groups/rules，不是 Kubernetes 清单或 Helm values；没有自动挂载、更新现有 release 或开启 Alertmanager。
现有 prometheus-values.yaml 的 Alertmanager 仍关闭，实际通知接收方/路由尚未配置，因此不宣称已经能送达人员。目标
DOWN/整个指标缺失需另由基础设施监控覆盖。

## 查证路径

1. 根据告警实例的采样范围配置确定需查证租户；使用该租户的受信运营凭证查询 REVIEW/URGENT 列表，原身份只在受控响应中出现。
2. 使用返回 kind/subjectId/recoveryGeneration 打开已有 tasks/detail，重新获取当前 native taskId/attempts/nextExecuteAt 与
   retryable。
3. 原生 FAILED 且仍需恢复时，按已有 tasks/retry 提交理由与准确前置条件。写路径继续加锁复核并写审计；列表不是执行授权。
4. 缺失/异常游标或外部事实仍不确定，保留证据和占用，升级人工调查渠道原交易；本入口没有“标记成功/失败”“释放”或“重新付款”操作。

人工工单认领/确认/备注、实际告警路由、终结证据归档及保留策略未在本切片实现。

## 测试与证据

- 领域/配置测试源码：阈值包含边界、部分秒、长期年龄/时钟回拨、默认/覆盖配置与错误装配。
- 服务测试源码：运营身份/权限、RR 租户作用域、游标与预取行、重复/越界/错误 kind 拒绝、子退款/申请身份、缺失游标及回拨后的未来时间记录继续分页。
- 实际 MySQL IT 输入：两个同租户同时间付款与另一租户隔离、稳定 ID 游标、截止时间、cursor
  联表、读快照内并行金融提交及新快照看到终结；退款联父申请/游标和终态退出列表。
- [规则测试源码](../deploy/payment-recovery-alerts-test.yml)：普通/紧急互斥、自定义阈值、旧样本、未初始化、关闭和 target
  DOWN。可在部署目录后续运行 promtool test rules payment-recovery-alerts-test.yml，本轮不执行。

以上没有运行证据。付款 IT 当前 15 个输入、退款 IT 9 个；新增查询不替代 R1/R2 验收，亦不表示 R3 真实
held/FAILED/Outbox、首提/集群配额和证据保留完成。
