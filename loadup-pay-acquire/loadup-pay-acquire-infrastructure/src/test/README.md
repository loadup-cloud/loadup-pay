# Acquire 真实持久化测试

`AcquiringFinancialPersistenceIT` 使用 Testify、真实 PostgreSQL Testcontainers、实际 Flyway、生成 Mapper、实际
Gateway/Codec/MapStruct，
并调用真实 PaymentInitiationService/Operations 与 PaymentProgressService/OutcomeOperations。只模拟跨服务 payment 的权威返回。
没有 mock 数据源、事务管理器、业务 Gateway 或 Task；商户通知使用真实事件逻辑，合约配置关闭商户发送。

## 测试入口

| 用例                                    | 真实数据库断言                                                                                        |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------|
| ORDER/DIRECT_PAY 原意图重建（两组输入） | 用对应原 payload/digest 重建完整订单/快照/归属，不把 DIRECT_PAY 送入 ORDER 解码器                     |
| 并发相同 actionKey                      | 一份 allocation、一次金额/次数预占、一份恢复游标/原生任务、一份订单 Task 快照；相同 paymentId         |
| A2 Task 后故障                          | 真实 publish 后抛错，预占/次数/allocation/恢复游标/native task/Task 全回滚，payment.accept 尚未调用   |
| A3 Task 后故障                          | 真实金额/receipt/费用确认与调整/Task 写后异常，全部回滚；原权威成功仍可查回，再次收敛与重放只生效一次 |
| 并发相同成功事实                        | 已付金额不重复、占用归零，一份 receipt/费用调整，仅新增一份 PAID 快照                                 |
| 已有 receipt 后同版本不同证据           | 拒绝冲突，金额/费用/receipt/Task 保持原记录                                                           |

故障注入类继承真实 OrderSnapshotEvents，先调用原 publish，再抛出一次测试异常；没有用 mock 或替代 SQL 代替 Task 写入。
Task 后台 worker 在测试配置关闭，避免投递影响原子性观察。计数通过实际 Mapper、JdbcTemplate 和 Testify 读取最终记录。

## 运行与当前状态

父 POM 的 Failsafe 在 verify 阶段执行 IT；普通 test 阶段不运行这些 IT。需 Docker 及当前已安装依赖；以下命令尚未执行：

```bash
mvnd clean verify -pl loadup-pay-acquire/loadup-pay-acquire-infrastructure -Dit.test=AcquiringFinancialPersistenceIT -Dspotless.skip=true -Dspotbugs.skip=true
```

待记录当前版本的编译及真实 PostgreSQL 运行结果；源码存在不代表通过。
这些用例验证本地受理/收敛与原生持久化边界，不代表真实 payment HTTP/JWT/TLS、渠道资金、商户签名投递或多实例故障演练已通过。
投影版本并发、其他 R1 组合故障，以及 review 全清单继续推进。
