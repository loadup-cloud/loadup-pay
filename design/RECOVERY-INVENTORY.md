# UNKNOWN 存量快照指标

更新：2026-10-27。对应 review R3 的一部分；当前仅完成源码与测试输入，没有运行验证。

## 数据口径

RecoveryInventoryReaderImpl 使用当前服务生成的 PaymentOrderDOMapper / RefundOrderDOMapper、TableDef 和 QueryMethods 聚合，
不创建表、不写手工 Mapper/替代 SQL、不回查外部渠道或额度服务，也不在指标 scrape 时执行数据库读取。

| kind    | 条件                                     | 金额                           | 首次 UNKNOWN 时间     |
|---------|------------------------------------------|--------------------------------|-----------------------|
| payment | 未逻辑删除且 payment_order.state=UNKNOWN | 这些付款的原 amount_minor 合计 | 最小 first_unknown_at |
| refund  | 未逻辑删除且 refund_order.state=UNKNOWN  | 这些子退款的 amount_minor 合计 | 最小 first_unknown_at |

仅统计 UNKNOWN，不把 INIT/PROCESSING/CLOSING 或成功/失败记录混为未知；其他状态仍可能存在未收敛占用，不能从该计数反推可释放金额。
金额是当前 CNY 记录的名义分额，不是 limitcenter held、账户余额、渠道净额或可退款金额；不是资金裁定/财务报表。
最老时间取当前 UNKNOWN 记录各自首次进入该状态的 first_unknown_at；不是创单、收款成功或最后查询时间。
Micrometer gauge 为浮点观测值，不承担精确分账、对账或余额计算。

聚合使用参数化 Spring JDBC SQL 的 COUNT、SUM 和 MIN，
投影对象为非表模型，再由 Spring MapStruct 转为只读 cell。空结果由 COUNT=0、SUM/MIN=NULL 形成显式空 cell；缺失/损坏结果或精确数值溢出不伪造成
0。

## 首次未知时间的写入契约

payment_order / refund_order 新增 first_unknown_at DATETIME (6) NULL，仅作为本地持久观测元数据，不加入公开
Command、冻结意图、摘要或金融领域聚合。
第一次从非 UNKNOWN 进入 UNKNOWN 时，实际金融 gateway 在既有锁序和版本 CAS 内，使用 共享 Clock 的系统本地时间（截断至微秒）与状态同一
UPDATE 保存；事务失败一起回滚。
已存在的值保留，重复结果、传输失败、任务重领、generation 换代均不重置。以后进入终态也保留，但库存只聚合当前 UNKNOWN 行。
数据库 CHECK 要求 UNKNOWN 必须有时间；不允许用 created_at/initiated_at 或当前采样时间静默兜底。

开发期同步修改原 payment acceptance / refund planning 初始化迁移，不保留旧格式或新增兼容回填；已有开发库需要按这版初始结构重建。
本轮只修改 schema 源码，没有执行迁移、重建、Flyway repair 或数据写入。生成 TableDef 继续由用户构建时的注解处理器生成。
旧的 .unknown.oldest.business.age 指标删除，统一为 .unknown.oldest.age，不保留当前开发期指标别名。

## 采样与范围

独立 ScheduledTask 名称 pay.payment.recovery.inventory，由现有 LoadUp Scheduler 唤醒；不是每笔原生资金任务，也不承担扣款/退款。

| 配置（loadup.modules.payment.recovery-inventory） | 环境变量                                    | 默认                                                    |
|---------------------------------------------------|---------------------------------------------|---------------------------------------------------------|
| enabled                                           | PAYMENT_RECOVERY_INVENTORY_ENABLED          | false                                                   |
| tenant-ids                                        | PAYMENT_RECOVERY_INVENTORY_TENANTS          | business profile 默认使用 PAYMENT_LIMITCENTER_TENANT_ID |
| interval-seconds                                  | PAYMENT_RECOVERY_INVENTORY_INTERVAL_SECONDS | 300，允许 1..86400                                      |

enabled=true 时必须明确非空租户范围，最多 100 个不同 ID；重复 ID 去重。不使用请求参数、当前调用方身份或全库租户绕过来扩大范围。
没有共享 MeterRegistry 或未启用时不执行读取，不创建私有 metrics backend。
每租户使用实际 REQUIRES_NEW / REPEATABLE_READ / readOnly 事务，查询明确 tenant_id，且没有 FOR UPDATE、写入或网络 IO。
各租户依次采样，不承诺跨租户同一数据库时点；范围内全部租户的付款/退款 cell 完整成功后才替换一个发布对象。

首次成功前库存/金额/年龄为 NaN，initialized=0。一次成功的空库存才发布 0；任一租户失败、缺失
kind、数据非法或溢出都保留原整个快照，刷新失败计数增加并继续暴露样本年龄。
单进程采样重叠被抑制，失败同样遵守采样周期；时钟回拨不会一直等待旧的未来采样时间。

默认未启用，避免未经查询计划和容量验收即周期扫描金融表；启用前后都不能将本源码视为负载已验证。
该任务与交易行写入无排他锁关系，但聚合成本、数据库快照/Undo 和实际查询计划仍需测量；本轮不据猜测新增索引。

## 指标

共同前缀 loadup.payment.recovery.inventory：

| 后缀                                          | 类型/标签                           | 含义                                                         |
|-----------------------------------------------|-------------------------------------|--------------------------------------------------------------|
| .unknown.count                                | Gauge，kind=payment/refund          | 最新完整配置范围内的 UNKNOWN 行数                            |
| .unknown.nominal.amount                       | Gauge，kind=payment/refund，minor   | 原记录名义分额                                               |
| .unknown.oldest.age                           | Gauge，kind=payment/refund，seconds | 最新样本最早 first_unknown_at 至当前本地业务时间；空库存为 0 |
| .sample.age                                   | Gauge，seconds                      | 最新完整样本距今时间，首次成功前 NaN                         |
| .sample.interval                              | Gauge，seconds                      | 配置采样间隔                                                 |
| .unknown.review.after / .unknown.urgent.after | Gauge，seconds                      | 服务端运营关注阈值，不是金融期限                             |
| .initialized                                  | Gauge                               | 是否至少发布过一次完整样本                                   |
| .configured.tenants                           | Gauge                               | 配置范围去重后的租户个数，不暴露 ID                          |
| .enabled                                      | Gauge                               | 本进程是否配置启用                                           |
| .refresh                                      | Counter，outcome=success/failure    | 完整采样批次结果，不是每行/每租户处理次数                    |

只有固定 kind/outcome 标签，不使用 tenant/order/payment/refund/attempt/task/账户/金额/异常文本等标签。
这些存量与之前 handler Timer.count/inflight 的调用口径分开。
多个副本读取同一数据库/同一范围时会重复报告存量，不能把副本 gauge 相加当平台总量；样本新鲜度和范围须一并解读。

## 证据与未完成范围

- Collector 单测源码：完整范围合并、只读租户作用域、scrape 不查询、周期抑制、失败不发布部分结果、NaN/空库存、无
  Registry/未启用不读取、时钟回拨。
- Cell 单测源码：数量/金额合并、最老时间、类型错配和溢出拒绝。
- 实际 MySQL IT 输入：付款两租户各自 UNKNOWN 聚合不串租户、空退款；退款 UNKNOWN
  聚合与原预占不变；无事务/可写事务拒绝。另覆盖首次状态写入后故障回滚时间戳、稍后真正提交才计时、重复未知结果不重置、终态保留原时间且退出库存。

上述均未编译或运行；没有数据库计划、真实 exporter、调度或多副本实测。
实际额度 held、FAILED 任务库存、Outbox
最老未交付/投影新鲜度、人工工单/通知路由及证据保留仍待实现；分级查证列表与告警规则源码见 [查证契约](RECOVERY-ESCALATION.md)
，不以本切片替代 R3 全部目标。
