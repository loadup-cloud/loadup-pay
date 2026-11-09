# realtime 架构

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## acquire 订单生产端接线

acquire 已为创单、金额占用/释放或成功落账、关单和延期的订单版本写入同事务完整快照 Outbox，再由独立 worker 调用现有
/api/realtime/projections/order。原始 createdAt 和更新 updatedAt 来自落库头部；重试发送原完整负载，不同步回查主交易服务。源版本从
0 开始，乱序旧版本可返回 STALE；相同 delivery 回放首次语义 ACK。acquire 挂载 SERVICE 凭证需 pay:realtime:projection:
order，默认服务标识 acquire。

当前自动来源已包含订单各版本和付款终态；付款中间态、退款/退款汇总生产端及重建仍待完成。新源码与测试未运行，尚未进行真实消息、鉴权或数据库链路验收。

## 退款来源接线与固定大小金额汇总

payment 已在退款计划、首次提交、UNKNOWN 与真实结果应用事务中生产逐笔退款完整快照，并通过独立 Outbox handler 调用
/api/realtime/projections/refund；订单退款金额聚合的创建占用/成功/失败版本另调用 /refund-summary。两者均携带原持久完整负载、独立
delivery/businessEvent/源版本，异步乱序由现有版本 CAS/语义回执处理，不回查主交易库。SERVICE 需分别授予 pay:realtime:
projection:refund、pay:realtime:projection:refund-summary。

OrderRefundSummaryEventDTO/TradeRefundSummary 已直接改为固定大小的累计已退/占用金额快照，删除历史 requests
列表及专用申请进度类，避免退款历史导致业务事务内消息不断膨胀。逐笔退款保留自己的完整状态、金额、原创建时间与渠道成功时间，分页查询；申请详情通过原
RefundFacade 获取。高版本金额汇总不允许累计已退下降，币种/订单/商户不可变，不逐笔累加或重写 acquire gross 已付金额。

渠道成功时间与本地审计时间属于独立时钟，保留真实值；不因来源精度差异对时间裁剪或强加先后顺序。缺汇总仍返回
null，来源领先时保留实际金额差和 amountsConsistent。新生产/投递和重构源码、测试均未运行；实际渠道、付款中间态来源、重建/检查点及数据库/消息链路验收未完成。

## 投影版本与 receipt 的真实数据库测试源码

新增 [ProjectionPersistenceIT 测试说明](realtime-service/src/test/README.md)
，对订单、付款、退款、退款汇总四类投影复用实际入口、事务、Gateway、生成 Mapper 和 Flyway。
覆盖并发 delivery、同版本冲突、乱序版本、receipt 插入后的原子回滚及原 ACK 重放，共 20 个参数化输入。
测试配置由显式 postgresql-it profile 启动；目前未编译或运行，不能视为 R1 的真实数据库证明，也未验证线上消息投递及重建流程。

## 付款非终态来源

payment 已补各已提交生命周期付款版本的独立完整快照，不需要 realtime 回查交易源。新增 INIT 0/空渠道到成功、先于订单保存、迟到
INIT 不重开终态的 MySQL IT
源码；未执行。生产边界、重放与尚未完成项见 [生命周期快照设计](../ARCHITECTURE.md)
。此前批次所写“付款中间态待补”是历史缺口记录。
