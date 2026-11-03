# Realtime 独立交易投影

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史 MySQL 记录推断。

应用间传输使用标准 HTTP，默认 8080。四个内部入口位于 /api/internal/v1/realtime/projections/；见[HTTP 契约](realtime-client/ARCHITECTURE.md)。

本服务拥有独立数据库和版本化读模型，消费受信 acquire/payment 的完整快照，不查询交易源、不参与付款提交、订单占用或额度决策。
订单、付款、退款及退款汇总生产端均已有源码；payment 新补 INIT 0、PROCESSING、UNKNOWN、CLOSING 的已提交版本快照，终态保持原独立投递。
当前源码与验证缺口见 [付款生命周期快照](../ARCHITECTURE.md) 和根能力矩阵；历史验证记录不覆盖后续修改。

| 能力         | 当前源码                                                                                                               |
|--------------|------------------------------------------------------------------------------------------------------------------------|
| 四类完整快照 | /api/internal/v1/realtime/projections/order、payment、refund、refund-summary，分别用独立 producer/aggregate/sourceVersion          |
| 版本处理     | 高版本 CAS；相同版本/内容幂等；低版本 STALE ACK；同版本不同内容或身份/终态回退拒绝覆盖                                 |
| 可靠回执     | 快照变更与 trade_projection_receipt 同事务，重放原 delivery 返回首次 ACK；不依赖来源在线                               |
| 商户查询     | /api/realtime/trades/detail、orders、payments、refunds，只接受可信 JWT 中的实际 merchant_id 和 pay:realtime:trade:read |
| 存储         | 四类投影及语义回执五表，完整强类型源 JSON + 查询索引列 + 校验哈希，BaseDO/MapStruct/生成 Mapper                        |
| 运行         | 默认业务装配，独立 PostgreSQL、JWT issuer/audience/JWK 配置                   |
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

业务运行需 REALTIME_JDBC_URL/USERNAME/PASSWORD、REALTIME_JWT_ISSUER_URI/AUDIENCE/JWK_SET_URI，默认使用业务装配。payment 出站配置 REALTIME_JWT_AUDIENCE、PAYMENT_REALTIME_TOKEN_FILE，tenant 默认复用
PAYMENT_LIMITCENTER_TENANT_ID，可由 PAYMENT_REALTIME_TENANT_ID 指定；令牌需 pay:realtime:projection:payment。复用
HttpTemplate 和挂载 SERVICE token 轮换读取，不转发商户令牌。

新增领域/接收/查询/规范编码/服务身份/发送与生产测试源码，未运行 IDEA、构建、测试、DDL、镜像或部署。当前部署清单与旧运行记录不证明本批业务
profile 已装配或联通。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

内部调用已删除 proto/Wire/Dubbo 生成与 Nacos profile。消费方使用 REALTIME_BASE_URL；源身份与 ACK 语义不变，运行验收见 [迁移清单](../HTTP_MIGRATION.md)。
