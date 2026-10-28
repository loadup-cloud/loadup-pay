# Realtime 独立交易投影

> 当前开发基线正切换至 PostgreSQL；各服务业务迁移已合并为单文件，RPC 协议位于各服务 client。下方历史验收描述保留其当时的数据库和运行状态；新基线尚需编译与真实 PostgreSQL 验证。

应用间传输已于2026-10-23统一为 gRPC，见[RPC 规范](../design/RPC.md)。本文件旧批次中的内部 HTTP 描述与 `*_BASE_URL`
要求由该规范取代；商户/运营 HTTP 入口继续保留。实际业务 gRPC 随本应用 `*-business` 构建和运行 profile 装配，端口50051；本批通过
IDEA MCP 完成六业务应用及依赖的 `clean test-compile`；未执行测试或部署。

本服务拥有独立数据库和版本化读模型，消费受信 acquire/payment 的完整快照，不查询交易源、不参与付款提交、订单占用或额度决策。
订单、付款、退款及退款汇总生产端均已有源码；payment 新补 INIT 0、PROCESSING、UNKNOWN、CLOSING 的已提交版本快照，终态保持原独立投递。
当前源码与验证缺口见 [付款生命周期快照](../design/PAYMENT-LIFECYCLE-SNAPSHOTS.md) 和根能力矩阵；历史验证记录不覆盖后续修改。

| 能力         | 当前源码                                                                                                               |
|--------------|------------------------------------------------------------------------------------------------------------------------|
| 四类完整快照 | /api/realtime/projections/order、payment、refund、refund-summary，分别用独立 producer/aggregate/sourceVersion          |
| 版本处理     | 高版本 CAS；相同版本/内容幂等；低版本 STALE ACK；同版本不同内容或身份/终态回退拒绝覆盖                                 |
| 可靠回执     | 快照变更与 trade_projection_receipt 同事务，重放原 delivery 返回首次 ACK；不依赖来源在线                               |
| 商户查询     | /api/realtime/trades/detail、orders、payments、refunds，只接受可信 JWT 中的实际 merchant_id 和 pay:realtime:trade:read |
| 存储         | 四类投影及语义回执五表，完整强类型源 JSON + 查询索引列 + 校验哈希，BaseDO/MapStruct/生成 Mapper                        |
| 运行         | realtime-business Maven/运行 profile，独立 MySQL、JWT issuer/audience/JWK 配置；默认骨架仍可单独装配                   |
| 待完成       | 重建/流检查点、查询新鲜度、多商户操作员授权及真实集成验收                                                              |

投影入口只允许 REALTIME_ACQUIRE_SERVICE_ID（默认 acquire）发布订单，REALTIME_PAYMENT_SERVICE_ID（默认 payment）发布付款/退款；各自权限为
pay:realtime:projection:order|payment|refund|refund-summary。producer 标签不是身份，服务 subject/service_id 必须来自已验证
SERVICE JWT。商户不能写投影，SERVICE 也不自动获得商户读取权限；当前未给无商户范围的 OPERATOR 或任意请求商户列表开放查询。

付款/退款可先于订单快照独立存储和分页，父行不存在不会阻塞接收。detail 没有订单时返回 NOT_FOUND，不从付款拼造订单总额。detail
仅返回订单头和独立退款汇总，子付款/退款使用各自分页接口，避免无上限列表或静默截断。分页默认1/20、显式非法值拒绝、上限100；时间范围左闭右开，按业务创建时间/稳定原ID倒序；所有
SQL 首先带 tenant/已验证 merchant 条件，使用生成 QueryWrapper 列。

订单 gross paid 不由最后一笔付款或退款重算。退款汇总没到时 refunded/net/refundSourceVersion 为 null，不能装作0。来源暂不同步、退款额领先订单
paid 时保留两个原来源版本和观测净额（可能为负），amountsConsistent=false，不裁零、不改原支付金额、不生成
availableToPay。单笔支付能力仍应查询 acquire/payment 的权威接口。

payload 保留完整强类型来源快照，索引列保留实际 merchant/order/refundRequest、状态、业务时间及
sourceVersion；读取重算规范哈希并核对索引与负载，拒绝未知字段/篡改。消费者本地 snapshotAt
不进入源内容哈希，合法重复不伪装为新的源事实。不同聚合、producer
和退款汇总版本不能互相比较。订单/付款/退款身份、原金额/创建时间不可变；高版本不能重开终态，退款累计不能减少或丢失已知请求。负载上限复用1MiB完整消息约束。

V20261023020000__trade_projections.sql 新增
trade_order_projection、trade_payment_projection、trade_refund_projection、trade_refund_summary、trade_projection_receipt。应用保存本地
DATETIME (6) 微秒，JVM/Clock/会话时区 系统默认时区，无 UTC 数据库列。读接口使用独立只读一致事务；投影写入和回执用本地事务/唯一键/源版本
CAS，不锁主交易库。无序 HTTP 完整快照以每行 sourceVersion 为推进依据，流级重建/checkpoint 尚未实现，不冒充全局就绪状态。

业务运行需 REALTIME_JDBC_URL/USERNAME/PASSWORD、REALTIME_JWT_ISSUER_URI/AUDIENCE/JWK_SET_URI，构建与运行同时选择
realtime-business。payment 出站新增 REALTIME_BASE_URL、REALTIME_JWT_AUDIENCE、PAYMENT_REALTIME_TOKEN_FILE，tenant 默认复用
PAYMENT_LIMITCENTER_TENANT_ID，可由 PAYMENT_REALTIME_TENANT_ID 指定；令牌需 pay:realtime:projection:payment。复用
HttpTemplate 和挂载 SERVICE token 轮换读取，不转发商户令牌。

新增领域/接收/查询/规范编码/服务身份/发送与生产测试源码，未运行 IDEA、构建、测试、DDL、镜像或部署。当前部署清单与旧运行记录不证明本批业务
profile 已装配或联通。

## 2026-10-23 acquire 订单生产端接线（新增源码）

acquire 已为创单、金额占用/释放或成功落账、关单和延期的订单版本写入同事务完整快照 Outbox，再由独立 worker 调用现有
/api/realtime/projections/order。原始 createdAt 和更新 updatedAt 来自落库头部；重试发送原完整负载，不同步回查主交易服务。源版本从
0 开始，乱序旧版本可返回 STALE；相同 delivery 回放首次语义 ACK。acquire 挂载 SERVICE 凭证需 pay:realtime:projection:
order，默认服务标识 acquire。

当前自动来源已包含订单各版本和付款终态；付款中间态、退款/退款汇总生产端及重建仍待完成。新源码与测试未运行，尚未进行真实消息、鉴权或数据库链路验收。

## 2026-10-23 退款来源接线与固定大小金额汇总（新增源码）

payment 已在退款计划、首次提交、UNKNOWN 与真实结果应用事务中生产逐笔退款完整快照，并通过独立 Outbox handler 调用
/api/realtime/projections/refund；订单退款金额聚合的创建占用/成功/失败版本另调用 /refund-summary。两者均携带原持久完整负载、独立
delivery/businessEvent/源版本，异步乱序由现有版本 CAS/语义回执处理，不回查主交易库。SERVICE 需分别授予 pay:realtime:
projection:refund、pay:realtime:projection:refund-summary。

OrderRefundSummaryEventDTO/TradeRefundSummary 已直接改为固定大小的累计已退/占用金额快照，删除历史 requests
列表及专用申请进度类，避免退款历史导致业务事务内消息不断膨胀。逐笔退款保留自己的完整状态、金额、原创建时间与渠道成功时间，分页查询；申请详情通过原
RefundFacade 获取。高版本金额汇总不允许累计已退下降，币种/订单/商户不可变，不逐笔累加或重写 acquire gross 已付金额。

渠道成功时间与本地审计时间属于独立时钟，保留真实值；不因来源精度差异对时间裁剪或强加先后顺序。缺汇总仍返回
null，来源领先时保留实际金额差和 amountsConsistent。新生产/投递和重构源码、测试均未运行；实际渠道、付款中间态来源、重建/检查点及数据库/消息链路验收未完成。

## Review R1：投影版本与 receipt 的真实数据库测试源码

新增 [ProjectionPersistenceIT 测试说明](realtime-infrastructure/src/test/README.md)
，对订单、付款、退款、退款汇总四类投影复用实际入口、事务、Gateway、生成 Mapper 和 Flyway。
覆盖并发 delivery、同版本冲突、乱序版本、receipt 插入后的原子回滚及原 ACK 重放，共 20 个参数化输入。
测试配置由显式 postgresql-it profile 启动；目前未编译或运行，不能视为 R1 的真实数据库证明，也未验证线上消息投递及重建流程。

## 2026-10-27 付款非终态来源

payment 已补各已提交生命周期付款版本的独立完整快照，不需要 realtime 回查交易源。新增 INIT 0/空渠道到成功、先于订单保存、迟到
INIT 不重开终态的 MySQL IT
源码；未执行。生产边界、重放与尚未完成项见 [生命周期快照设计](../design/PAYMENT-LIFECYCLE-SNAPSHOTS.md)
。此前批次所写“付款中间态待补”是历史缺口记录。
