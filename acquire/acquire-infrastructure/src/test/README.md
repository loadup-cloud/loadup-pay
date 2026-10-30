# Acquire 真实持久化测试

`AcquiringFinancialPersistenceIT` 使用 Testify、真实 PostgreSQL Testcontainers、实际 Flyway、生成 Mapper、实际
Gateway/Codec/MapStruct，
并调用真实 PaymentInitiationService/Operations 与 PaymentProgressService/OutcomeOperations。只模拟跨服务 payment 的权威返回。
没有 mock 数据源、事务管理器、业务 Gateway 或 Outbox；商户通知使用真实事件逻辑，合约配置关闭商户发送。

## 已补测试源码

| 用例                                    | 真实数据库断言                                                                                          |
|-----------------------------------------|---------------------------------------------------------------------------------------------------------|
| ORDER/DIRECT_PAY 原意图重建（两组输入） | 用对应原 payload/digest 重建完整订单/快照/归属，不把 DIRECT_PAY 送入 ORDER 解码器                       |
| 并发相同 actionKey                      | 一份 allocation、一次金额/次数预占、一份恢复游标/原生任务、一份订单 Outbox 快照；相同 paymentId         |
| A2 Outbox 后故障                        | 真实 publish 后抛错，预占/次数/allocation/恢复游标/native task/Outbox 全回滚，payment.accept 尚未调用   |
| A3 Outbox 后故障                        | 真实金额/receipt/费用确认与调整/Outbox 写后异常，全部回滚；原权威成功仍可查回，再次收敛与重放只生效一次 |
| 并发相同成功事实                        | 已付金额不重复、占用归零，一份 receipt/费用调整，仅新增一份 PAID 快照                                   |
| 已有 receipt 后同版本不同证据           | 拒绝冲突，金额/费用/receipt/Outbox 保持原记录                                                           |

故障注入类继承真实 OrderSnapshotEvents，先调用原 publish，再抛出一次测试异常；没有用 mock 或替代 SQL 代替 Outbox 写入。
Outbox 后台 worker 在测试配置关闭，避免投递影响原子性观察。计数通过实际 Mapper、JdbcTemplate 和 Testify 读取最终记录。

## 运行与当前状态

显式 `postgresql-it` Failsafe profile，默认单测不启动容器。需 Docker 及当前已安装依赖；以下命令尚未执行：

```bash
mvnd clean verify -pl acquire/acquire-infrastructure -Ppostgresql-it -Dit.test=AcquiringFinancialPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

当前仅有源码，未进行 IDEA、编译、单测、Docker 或 PostgreSQL 运行。R1 尚无本轮通过证据。
这些用例验证本地受理/收敛与原生持久化边界，不代表真实 payment Dubbo Triple/JWT/TLS、渠道资金、商户签名投递或多实例故障演练已通过。
投影版本并发、其他 R1 组合故障，以及 review 全清单继续推进。
