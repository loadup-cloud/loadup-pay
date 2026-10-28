# 后置交易对账详细设计与核心契约

更新：2026-10-27。用户已确认首期支付宝/微信官方交易日账单，同时核对收款和退款，按实际收款账户与账单日期分批；银联在产品确定后接入。

当前已有 client/domain/app/infrastructure/adapter/rpc/bootstrap 七层源码：纯核对、批次、独立存储、原生后台任务、
HTTP/gRPC 与独立业务装配。微信 API ALL 格式解析源码已补；File/Transfer 手工 CHANNEL 来源和 payment 微信权威快照/完整
LEDGER 导入已接源码；官方文件取得/验签来源、支付宝解析与对应源端日期仍待实现。
**全部新增源码未构建、执行测试、应用迁移或部署。** 来源集成和查证部分仍是后续契约，不能声称已上线。

## 服务边界和依赖

reconciliation 拥有独立数据库，后置运行，不参与 acquire/payment 的受理、Y2 首提、额度许可或渠道 ACK。
不依赖 realtime 查询来证明资金事实；realtime 的最终一致投影不能证明某日交易集合完整。
差错不会直接修改付款/退款表、成功金额或额度，更不会因缺失快照自动退款、放行新付款或释放 UNKNOWN 占用。

集成复用 LoadUp FileResourceFacade 管理原文件与业务引用、TransferHandler/TransferContext 运行导入与报告任务、
生成 Mapper/MyBatis-Flex/MapStruct 管理本服务数据、DelayedTask 负责持久执行、Outbox 负责后续证据查证请求。
已有 Transfer 不代表核对业务已经接入；不能把 Transfer 的任务成功、最大已见事件时间或下载 404 当作对账完成证据。

当前 root reactor 与源码/POM 门禁纳入 reconciliation 七层模块；构建/部署选择器、Docker 允许路径与第七组
Deployment/Service/Headless 清单已同步。
默认启动仍为健康骨架，构建和运行都需选择 reconciliation-business 才装配业务，并提供独立数据库、JWT 与受控账户目录。

## 渠道账单与规范化

支付宝采用[官方交易账单说明](https://opendocs.alipay.com/open/03axlq.md)，区分业务交易账单和账号资金流水。
下载作用域须对应实际 PID/服务商代调用授权的收款账号，不按签名应用或服务商平台账户混合核对所有子商户。

微信采用[官方交易账单接口](https://pay.weixin.qq.com/doc/v3/merchant/4013071227.md)的日账单；
当前 ALL / SUCCESS / REFUND 覆盖不同记录集合，不能拿仅 SUCCESS 文件声称退款已齐备。
API 当前给出文件 SHA1；下载适配需先按已验签 API 返回的算法和摘要校验原下载文件，再计算本服务独立 SHA256。
本服务 artifactSha256 是证据身份，不冒充微信平台提供的摘要。

规范化 profile 由可信账户目录选定，冻结格式、列名/编码、日期口径和状态映射；不能由商户传入任意映射表达式。
支付宝/微信的表头、汇总尾部、编码、压缩、退款日期与状态需由各自解析器明确处理。
退款行存在不直接等于资金已退回：根据实际来源字段保留 PROCESSING / SUCCEEDED / FAILED / UNKNOWN。
退款日期按冻结 profile 的账单口径选取，不能用原付款日或服务端导入日猜测。

规范化行使用正数整数分和 CNY；收款使用原标价/订单金额，退款使用本次子退款的原请求金额，
不拿实收净额、折后买家实付、手续费或负数取绝对值替代。
收款键为原 channelTransactionNo；退款键为原支付交易号 + 原商户子退款请求号。
支付宝不能假设存在与微信 refund_id 等价的统一退款流水；原商户整单退款号也不能替代分配后的渠道子退款号。

核对 key 还受整个输入 manifest 的 channel、actual accountScopeHash、billDate 和 statementProfile 约束。
manifest 的日期表示账单范围，不要求每行的原付款日期与账单日期相同。
本服务不引入消费者用户资源、余额账户、清算账或通用会计分录。

## 两侧完整输入与来源证明

CHANNEL 输入为原渠道账单；LEDGER 输入为 payment 提供的受控权威范围快照/导出，收款与退款分别按相同 profile 选取。
LEDGER 不接受操作者上传 CSV 充当权威源，也不以“已消费到某个成功事件”声明整日集合完整。
源服务需生成可重放的 capture/export id、范围、完整性记录和不可变文件，证明查询窗口与截止快照一致；当前微信源端采集快照及
gRPC 完整分页导入已有源码，详见 [采集契约](PAYMENT-RECONCILIATION-CAPTURE.md)；未运行验收。

渠道下载经过已验签渠道 API 定位；人工上传时记录操作者和来源声明，文件哈希只证明内容没有变化，不证明文件真实性。
无账单可能是尚未生成或查询窗口错误；不能自动转换为完整空文件。双侧空输入只在取得各自明确完整零记录证据后封存。

导入器在事务外读取固定 FileResource，验证权限/来源、原字节哈希、解压/编码、账户、账期、所有记录、EOF 和尾部汇总。
未知格式/重复业务键/重复来源行序号/合计不符均拒绝整个输入，不跳过坏行后宣称成功。
当前配置限制每输入默认 10000 行、原文件 16 MiB；范围为完整 bounded dataset，不声称已提供大文件流式导入能力。

封存 manifest 固定 side、scope、artifactId、原 SHA256、规范化 SHA256、sourceCaptureId、parserRevision 和支付/退款各笔数及金额。
规范化 SHA256 采用私有版本化长度前缀编码，覆盖全部规范化行，包括匹配行的键、请求号、金额、币种、状态与来源序号。
不使用公开 Command/DTO JSON 或全局 JSON 命名规则计算业务证据身份。
ReconciliationDataset 重新验证规范化哈希、笔数/金额与唯一性；records 是不可变副本。
封存后不得修改输入或其行，更正文件/解析规则必须登记新 artifact 与新批次。

## 批次与比较

`INIT → READY → RUNNING → MATCHED / DIFFERENCES`。首次状态统一 INIT。
只有双侧同作用域且独立 capture 的封存 manifest 才可 READY；半文件或尚在导入中的一侧不能触发核对。
相同 manifest 的重复 attach 不改变版本；同侧换文件拒绝。begin 绑定原 runId，重放相同 runId 不创建新执行权。

ReconciliationMatcher 对排序后两侧记录做 merge，保留：CHANNEL_ONLY、LEDGER_ONLY、AMOUNT_MISMATCH、
REQUEST_MISMATCH、STATE_MISMATCH。相同键同时存在多种不一致时保存全部原因，不用一个错误掩盖另一个。
退款与付款分别计算总额，退款不冲减成功支付金额。

结果摘要绑定两份完整 manifest、规范化内容哈希、匹配数和有序差错内容，结果 hash 与遍历顺序无关。
complete 只接受原 runId 和原两份 manifest；相同完成结果可重放，不允许用新输入/新执行号覆盖已完成结果。
MATCHED 仅表示这两份已封存数据按冻结规则一致，不是额外资金事实，也不证明此后没有补发/更正账单。

## Client API 契约

`ReconciliationFacade` 已定义：

| 方法        | 参数                                     | 语义                                                                                               |
|-------------|------------------------------------------|----------------------------------------------------------------------------------------------------|
| create      | requestKey、channelAccountCode、billDate | 由可信目录解析实际账户/profile，租户/调用方来自身份上下文                                          |
| attach      | batchId、side、artifactId                | 只引用认可导入/导出适配器签发的封存 artifact，不接受 raw URL、文件路径、complete=true 或客户端合计 |
| start       | ReconciliationBatchStartCommand(batchId) | 本地登记原运行与持久任务；远端文件/源服务 I/O 不跨本地事务                                         |
| detail      | batchId                                  | 返回原输入摘要、状态、runId、版本与结果摘要                                                        |
| differences | batchId、kind/reason、page/size          | 查询本服务差错；默认 1/20，上限 100，租户/账户权限来自身份                                         |

app 已直接实现 Facade；HTTP POST 为 /api/reconciliation/batches/{create,attach,start,detail,differences}。
提供方 reconciliation-rpc 定义 ReconciliationRpc 的 Create/Attach/Start/Detail；差错分页当前由运营 HTTP 提供。
MapStruct 使用 Spring 组件；Wire optional 保留未开始/未完成与显式零值，日期为严格 yyyy-MM-dd，int64 不经浮点数，未知枚举与不完整完成摘要拒绝。
所有操作仅已验证 SERVICE/sub=service_id，权限分别为 pay:reconciliation:batch:create/attach/start/read；租户不从请求取值。
当前账户目录明确 service-ids 授权，原批次还要求原 caller 匹配；目录默认空。原 profile 随批次冻结，当前账户身份或调用方授权撤销仍拒绝。
差错终态查询使用通用 PageResult/PageDTO/PageResponse，count 与页在一个只读 RR 快照内；应用核对原 run 与筛选结果，不远程查交易。
不提供 UpdatePaymentStatus、直接退款、直接改额度或客户端提交“匹配成功结果”的接口。
手工导入的 upload/submit/detail/retry 见下文；不提供任意 Transfer handler/options 入口。
差错复核/审批已有独立 head/proposal/event、运营 API 和私有证据引用源码，见 [复核设计](RECONCILIATION-REVIEW.md)
；显式可信源端查询与观察留档源码见 [查证设计](RECONCILIATION-VERIFICATION.md)，实际验收与受控金融处置仍待继续。

## 数据库与提交边界

[业务设计 SQL](sql/reconciliation-draft.sql)已对应到 infrastructure 下
V20261027000000__reconciliation_core.sql 迁移源码；本轮没有执行 DDL。业务 profile 的 classpath:db/migration 同时包含框架
Sequence/DelayedTask 迁移。

| 表                        | 责任与约束                                                                                                  |
|---------------------------|-------------------------------------------------------------------------------------------------------------|
| reconciliation_batch      | 原 caller/requestKey 幂等、实际账户/账期/profile、双输入引用、原 runId、版本与结果 hash；同请求参数变化拒绝 |
| reconciliation_input      | FileResource 引用、来源 capture、原/规范化哈希、原 parser revision、总额与 INIT/SEALED/REJECTED 导入状态    |
| reconciliation_entry      | 每输入唯一业务键 hash 与唯一来源 ordinal，保存完整键防止哈希别名、原请求号、正数分/CNY 与规范化状态         |
| reconciliation_difference | 每批次唯一业务键，引用双侧不可变行并保存全部差异标志；单边记录和双边不一致的约束分开                        |

DO 已继承 BaseDO，Gateway 消费处理器生成 Mapper/TableDef，不手写生成类或持久 Mapper/SQL 方法。
输入头和全部行在一个调用方写事务 insertSealed，封存后无更新入口；完整行集合加载时再次复核规范化哈希、重复与总额。
start 锁批次并登记唯一原 runId，与 DelayedTask.register 同事务提交；native bizId 为原 runId。
任务先只读 RR 载入双侧原封存数据，再事务外执行纯比较，最后锁批次、写全部差错与完成摘要并 CAS 版本，同一事务失败全部回滚。
差错通过原输入/key hash 批量定位完整行并核对完整键及原行，不能因哈希命中就信任引用；分页同样核对原运行和双侧输入。
重复原任务只能返回相同原结果，终态不创建新运行。native token fencing 由 DelayedTask 管理，业务元数据核对不替代 ACK 领取权。
只读/网络/文件处理与业务写入分开，所有时间仍使用系统本地 Clock/数据库审计时间。
不同账户、账期或 profile 不合并；不跨 payment/reconciliation schema 联表或写资金表。

## 当前证据与后续

已补纯领域测试源码：原支付与多次部分退款、处理中退款不冒充成功、全部不一致原因、双向单边、账户/日期/profile 隔离、
重复键/来源序号/错误合计/金额溢出/内容篡改、输入不可变、原运行与完成幂等。未执行。

应用、JWT/账户授权、协议 optional/int64/日期、MapStruct 存储转换及微信文件边界测试源码已补，同样未执行。
待继续：官方渠道下载/验签来源、支付宝解析、支付宝源端日期/完整导出、差错查证/复核，
以及所有构建、单测、真实数据库原子性/并发、真实 JWT/RPC/文件/渠道与部署验收。

## 微信 ALL 格式解析切片

WechatTradeStatementParser 固定 profile/parserRevision=wechat-trade-all-20260116，依据
[官方交易账单详情](https://pay.weixin.qq.com/doc/v3/merchant/4013071246.md)和
[账单日期/快照口径](https://pay.weixin.qq.com/doc/v3/merchant/4013071215.md)。
只接受 API ALL 的完整 27 字段表头，明细/汇总每个单元格都有反引号前缀；未知/旧格式、SUCCESS 或 REFUND 单类型表头不混入 ALL。
严格 UTF-8、字节/行数/单行上限，验证每行主商户/实际子商户、CNY、交易时间所在账日；直连特约商户号必须为 0。
全文件验证末尾总笔数、应结订单/退款/充值券退款/手续费/订单原总额/申请退款原总额七项，尾部后必须 EOF。
重复键、金额精度/范围/累加溢出或损坏行均拒绝整个输入，没有跳过机制。

付款取订单金额，退款取申请退款金额，不拿应结金额或净额替代。退款 SUCCESS/PROCESSING/FAIL/CHANGE 分别规范为
SUCCEEDED/PROCESSING/FAILED/UNKNOWN；账单所记为出账快照，后续查证结果不覆盖原文件。
REVOKED 目前缺少独立补偿身份规范，整份拒绝并等待对应业务处理，不能制造普通商户子退款号或丢弃撤销行。
显式完整零汇总可解析为空集合，但“无文件/404/仍在生成”不可伪造零记录。

解析器只检查内容与作用域，不取得文件、不做渠道验签、不授予 LEDGER 来源资格；产生的 SHA256 仅为原字节证据标识。
尚待外层从可信账户/下载回执选择 Account、冻结文件和来源 capture，并在封存前完成官方摘要/文件权限/来源完整性校验。

## 手工 File/Transfer 来源与封存流程

新增 reconciliation_import 保存来源声明、原 caller/requestKey/digest、固定实际账单账户、fileId/storageId/长度/SHA256、原
taskId、逻辑状态与拒绝码。
来源固定 MANUAL_UPLOAD，import id 同时是原 artifact/capture id；不能把这类输入称为已验证的官方渠道下载。
目前只有微信固定 ALL profile 的手工 CHANNEL 路径；LEDGER 已接 payment 微信受控快照/完整分页导入源码，不接受操作者文件。

1. 受信 SERVICE 上传有界原字节至 FileResourceFacade；使用私有 S3 binder。返回 id/长度/hash，不返回存储位置或预签名 URL。
2. submit 输入 batchId/requestKey/fileId/sourceSha256；校验原 caller/租户/当前账户，原请求先重放；事务外 inspect 实际文件并对比预期
   hash。
3. 本地写锁批次，复核作用域/账户、无原 CHANNEL 和其他 INIT 导入；文件业务引用、Transfer 任务与导入意图同库提交。Native
   options 只含 importId。
4. Native Transfer 在事务外打开原文件，handler 读 RR 查证原持久任务
   RUNNING/tenant/owner/handler/file，及业务文件引用与冻结账户。请求参数不授予执行权。
5. 解析前固定完整长度与原 hash，短读/I/O 保留 INIT；完整原字节后再判断账单格式与所有汇总。确定坏文件 REJECTED，原生任务
   FAILED，无封存行。
6. 原批次锁 → 原导入锁 → 封存输入/全部规范行 → 批次 CHANNEL 引用 → 导入 SEALED，统一本地事务。更正文件新请求，不能改变原文件或绕过终态。
7. 任务状态独立于逻辑封存；报告/ACK 出错可出现 SEALED+FAILED，显式重试原任务后检查原 manifest 跳过解析。重复处理不能增加批次版本或换源。

长期业务文件引用不随 Transfer 临时引用释放，成功和拒绝证据都保留。文件保留/清理、崩溃遗留 RUNNING 的受控恢复仍待实现；
当前不取消/重建原任务、不自动重领 RUNNING，不把 native 失败或空文件作为资金结果。

HTTP 为 /api/reconciliation/imports/{upload,submit,detail,retry}，权限 pay:reconciliation:import:
upload/submit/read/retry。
submit 的 sourceSha256 是期望字节身份，不是完整性/真实性声明；不接受租户、账户标识、side、parser、总额或 sourceKind 覆盖。
配置和身份细节见 [模块说明](../reconciliation/README.md)。新增 V20261027010000__reconciliation_import.sql 源码及真实
MySQL IT 源码；未运行。

## payment 退款归账时间前置证据

[微信退款查询](https://pay.weixin.qq.com/doc/v3/merchant/4013071041.md)和
[退款申请说明](https://pay.wechatpay.cn/doc/v3/merchant/4012810597)定义 create_time 为微信受理退款申请的时间。
它与 success_time 分开；交易账单按退款发起受理的日期出账，不能用本地提交时间、原收款日或退款成功日替代。

当前微信适配器从已验签、已核对原身份/金额的提交和查询响应解析 create_time，转为注入 Clock 的本地时间并截断微秒。
RefundObservation.acceptedAt 是可空独立元数据；没有字段不默认填当前时间。支付宝未确认对应口径，不推测复用退款成功时间。

payment 的 refund_acceptance_evidence 在本服务事务内关联原 APPLIED/IGNORED 回执、退款/收款原标识、实际账户、金额和首次受理时间。
该表独立于金融聚合，元数据写入不推进退款版本、updated_at 或资金量；只有原有退款成功/失败证据决定资金与容量。
同一事实重放不重写；后续受理时间不同保留首次值并记录 REFUND_ACCEPTANCE_TIME_CONFLICT，不丢弃正确的金融终态。
不匹配或 CONFLICT 回执、早于原收款时间的受理信息不得登记。回执与元数据故障统一回滚，不产生孤立时间证明。

退款事实摘要使用 refund-facts-v2 覆盖可空 acceptedAt；开发期不兼容旧构造/摘要或推测回填旧记录。实际迁移/重建未执行。
权威 capture 尚待实现：必须明确所有范围、日期和未定记录，不得把 acceptedAt 缺失的已提交退款简单过滤后宣称完整日数据。

## P1 微信 LEDGER 权威快照接线

payment 独立 INIT→SEALED/BLOCKED 源采集与原 DelayedTask、真实只读 RR、永久源行、退款渠道受理日证据、RPC 与 FAILED 条件重试已有源码。
对账 `/ledger/prepare` 按原 scope 发源请求；`/ledger/import` 只读取同范围 SEALED，校验完整分页/空 EOF/总计/hash 后保存同
hash 的原规范二进制文件。
文件永久引用、输入全行和批次 LEDGER 原引用在本库同事务提交；不会同步写资金或把源 INIT/BLOCKED 当空成功。
完整状态/身份/预算/证据保留边界见 [采集契约](PAYMENT-RECONCILIATION-CAPTURE.md)。测试源码与真实 MySQL 场景已补，未编译/运行。
官方自动渠道来源、支付宝解析及差错复核仍待继续，整个 P1 未标完成。

## 2026-10-28 差错独立复核

复核独立于原批次/差错/资金状态，INIT/PROPOSED/APPROVED/REJECTED 与版本、不可变提案/命令审计、原 run/result/key 引用及两个
SERVICE 身份的独立审批源码已补。File 证据 inspect 在事务外，永久引用/提案/回执/head CAS 同库提交；原命令重放保留第一次
ACK。详情/历史只读 RR。详见 [复核设计](RECONCILIATION-REVIEW.md)。未运行构建、测试、DDL、真实权限或存储验收；获批
NEEDS_SOURCE_VERIFICATION 不触发金融操作，显式源端只读查询/留档源码已补，实际验收仍待完成。
