# Realtime 架构

接口与配置见 [README](README.md)。本模块只拥有读模型，不反向修改源交易或资金事实。

## 来源与投递

acquire 在创单、占用、成功/释放、关单和延期事务内登记完整订单快照；payment 在已提交付款生命周期、退款计划/执行/结果及退款金额汇总事务内登记各自完整快照。订单来源版本从 0 开始。

各来源使用独立 delivery/businessEvent/aggregate/sourceVersion，通过 Task 重放原持久负载；不按发送时刻重取源数据。原始 createdAt/updatedAt 和渠道成功时间保留各自语义，不以消费者时钟裁剪或伪造先后顺序。

## 版本与事务不变量

- 四类投影各自维护来源版本，不跨 producer/聚合比较版本。身份、币种、原金额和创建时间不可变，终态不被 INIT 或未知状态重开。
- 高版本以 CAS 推进；同版本同内容幂等，低版本返回 STALE ACK，同版本不同内容或不合法金额/身份/状态拒绝覆盖。
- 完整强类型 payload、索引列及规范哈希必须一致；读取重新校验，snapshotAt 不进入源内容哈希。单消息上限 1 MiB。
- 投影变更和 trade_projection_receipt 同事务；原 delivery 重放返回首次语义 ACK，不依赖来源在线。回滚不得留下孤立 receipt 或半个投影。
- 付款/退款可先于订单落库，不因缺父投影阻塞接收，也不拼造订单总额。商户读模型按认证 merchant_id 过滤，在本地默认 schema 运行。

## 金额与查询

订单 gross paid 取订单来源，不能由最后一笔付款或退款重算。退款汇总使用固定大小累计已退/占用快照；更高版本不得减少累计，逐笔退款单独分页，申请详情归 payment。

缺汇总时 refunded/net/refundSourceVersion 为 null。不同来源暂不同步、退款额领先订单 paid 时保留两者版本、实际净额（可为负）和 amountsConsistent=false；不裁零，不改变原金额或提供 availableToPay。

查询使用本地只读一致快照，时间范围左闭右开、业务创建时间和稳定 ID 倒序；通用分页约束见 [commons-dto](../../loadup-framework/loadup-commons/loadup-commons-dto/README.md)。不锁主交易库。

## 能力限制与待验收

未实现流级重建/checkpoint、查询新鲜度证明及多商户操作员授权。四类来源已有生产/消费入口，仍需验证真实 HTTP/JWT、乱序重复、跨主体拒绝、PostgreSQL 并发与回滚、进程重启投递。测试范围见 [持久化测试说明](loadup-pay-realtime-service/src/test/README.md)。

## 阶段 3 事件边界

共享事件 DTO 支持新 canonical 类型与各自原冻结标签；退款汇总入口继续限制自己的旧 realtime 标签，不能接收旧 fee 标签。来源认证、payload hash、sourceVersion、同版本冲突和 receipt/投影原子应用不变。requestDigest 仍绑定收到的原 eventType，不重算历史回执；投影不是金融事实裁决者。详细映射、回查保留原因和待运行验收见[实施记录](../design/PHASE3_IMPLEMENTATION.md)。本轮未构建或测试。
