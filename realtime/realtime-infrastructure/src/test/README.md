# Realtime 真实投影持久化测试

`ProjectionPersistenceIT` 使用 Testify、真实 PostgreSQL Testcontainers、实际 Flyway、生成 Mapper、Gateway、Codec、MapStruct、事务管理器与
`TradeProjectionService`。
只由测试提供可信服务主体；没有 mock 数据源、事务、投影或 receipt 仓储，也不需要调用上游服务。

## 已补测试源码

五组参数化用例覆盖订单、付款、退款、订单退款汇总四类投影，共 20 个输入；另有一例付款完整生命周期，共 21 个测试输入。

| 用例                              | 预期数据库边界                                                                                                                                 |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| 并发相同 delivery                 | 一个投影、一份原始 receipt，两个调用取得相同原始 ACK                                                                                           |
| 同版本不同内容                    | SOURCE_CONFLICT，不改原行，不为冲突事件写 receipt                                                                                              |
| 并发首次写入且同版本内容冲突      | 唯一一个成功，竞争失败者不能覆盖赢家或留下 receipt                                                                                             |
| 并发较新/较旧版本及再次迟到事件   | 最终保留最大版本与对应内容；迟到事件返回 STALE 并单独留下接收 receipt                                                                          |
| receipt 插入后故障与 ACK 丢失重放 | 实际 receipt 插入后抛错，投影和 receipt 一起回滚；重试可正常提交，原 delivery 保留原 ACK，新 delivery 返回 DUPLICATE，同 delivery 改内容被拒绝 |

故障注入继承实际 receipt Gateway，先执行生成 Mapper 插入，再抛一次测试异常。
测试中的 SQL 只读取固定枚举限定的投影表及 receipt 表，生产仓储继续使用生成 Mapper。
ACK 丢失用调用方丢弃返回值建模，不声称已验证实际 gRPC 网络故障。

## 运行与当前状态

显式 `postgresql-it` Failsafe profile；默认单测不会启动这些 IT。需要 Docker 和当前依赖，以下命令尚未执行：

```bash
mvnd clean verify -pl realtime/realtime-infrastructure -Ppostgresql-it -Dit.test=ProjectionPersistenceIT -Dskip.spotless=true -Dskip.spotbugs=true
```

当前只有测试源码，未运行 IDEA、编译、单测、Docker 或 PostgreSQL。R1 仍无本轮通过证据。
这些用例不证明真实 gRPC/JWT/TLS、上游 Outbox 投递、查询负载或投影重建/检查点已经验收。

## 2026-10-27 付款生命周期用例源码

新增 INIT version 0（无渠道）→ PROCESSING → UNKNOWN → CLOSING → SUCCEEDED，付款可先于订单保存；迟到 INIT 返回 STALE，新
delivery 的同一终态返回 DUPLICATE，更高版本 UNKNOWN 不能重开成功终态。仍使用实际 PostgreSQL/投影/receipt，尚未构建或执行。
