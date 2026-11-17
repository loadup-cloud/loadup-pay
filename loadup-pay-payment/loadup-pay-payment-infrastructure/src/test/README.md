# Payment 持久化集成测试

使用 Testcontainers PostgreSQL、实际 Flyway、生成 Mapper、Gateway、本地事务、Task 与 Task。IT 由父 POM 的 Failsafe 在 verify 阶段执行；普通 test 阶段不运行 IT，不连接业务库。

## 测试入口与边界

| 测试类/范围 | 待证明的实际边界 |
|---|---|
| RecoveryPersistenceIT | 游标/首任务/后继同事务回滚、并发只提交唯一后继、只读 RR 与写事务并行、FAILED 重试与审计原子性 |
| FinancialPersistenceIT | Y2 双赢家竞争、ticket 丢失不重建、attempt 更新回滚、FIFO 退款容量竞争与计划多表回滚 |
| PaymentResultPersistenceIT | Y3 claim/capacity/结果/receipt/各目标 Task 原子回滚，原请求查询恢复，同事实不同 receipt 去重，流水跨别名拒绝，关闭证据 |
| PaymentResultPersistenceIT 的恢复 handler 场景 | 原生 claim/finish、旧租约与重新领取、旧查询晚回、唯一后继、未来 generation 拒绝；不得重获首提权 |
| RefundRecoveryHandlerPersistenceIT | 退款请求/子单/容量/汇总/receipt/事件共同回滚，真实恢复 handler 的租约/代次交错，同事实不重复退款 |
| PaymentCapturePersistenceIT | 未决阻断、账户/范围约束、单 RR 一致性、封存分页不受后续事实改变、capture 头/行共同回滚 |
| UNKNOWN 库存/列表场景 | 聚合与分页关联、首次 UNKNOWN 时间只初始化一次、旧版本拒绝、终态退出、可信租户过滤 |
| PaymentCloseEvidenceStorageConverterTest 及关闭场景 | 原绑定/金额/epoch/时间映射；关闭确认独立提交、Y3 失败后用旧确认收敛，不重复 close 或产生退款容量 |

故障注入应先执行实际数据库写入再抛异常；不能用调用次数或事务替身证明 SQL 回滚。Task 后台发送在测试中关闭，真实入库不等于真实投递。

渠道 IO、可信身份及未触达分支使用受控替身，不读取生产密钥。恢复场景断言原请求号/账户/金额/epoch 不变，submit/submitRefund 不因任务重试再次执行。Y2 前置与恢复前置状态不是完整授权链证明。

租约并发使用可推进 Clock 和同步屏障，不靠 sleep、修改机器时区或伪造 native 状态。测试清理仅限自身未完成任务及隔离数据，保留金融事实与回执。

## 执行

需要 Docker、Java 25、Maven Daemon 及当前配套制品。在 Pay 根目录选择相关类执行：

```bash
mvnd clean verify -pl loadup-pay-payment/loadup-pay-payment-infrastructure \
  -Dit.test=RecoveryPersistenceIT,FinancialPersistenceIT \
  -Dspotless.skip=true -Dspotbugs.skip=true
```

可替换为表中其他 IT；保持窄范围 clean。测试配置中的 lease、批量、重试和 jitter 仅服务确定性测试，不应复制为生产参数。

## 待验收

上述入口需保留当前版本的编译和真实 PostgreSQL 运行结果。仍需实际 Scheduler/Dispatcher、多实例停机、网络丢包、JWT/TLS、回调验签、JSAPI/H5 action、渠道幂等、确认写前崩溃及跨服务资金链路验证；本地受控事实不代替这些证据。源码存在不表示通过，验收要求见 [总体架构](../../../../ARCHITECTURE.md)。
