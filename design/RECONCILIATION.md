# 后置交易对账详细设计与核心契约

更新：2026-10-27。用户已确认首期支付宝/微信官方交易日账单，同时核对收款和退款，按实际收款账户与账单日期分批；银联在产品确定后接入。

本轮新增 reconciliation-client / reconciliation-domain 与纯核对内核、批次生命周期、测试源码。
**应用编排、文件导入、持久化、RPC/HTTP、调度与部署尚未实现。** 本页中的存储和集成部分是后续实施契约，不是当前已上线能力。

## 服务边界和依赖

reconciliation 拥有独立数据库，后置运行，不参与 acquire/payment 的受理、Y2 首提、额度许可或渠道 ACK。
不依赖 realtime 查询来证明资金事实；realtime 的最终一致投影不能证明某日交易集合完整。
差错不会直接修改付款/退款表、成功金额或额度，更不会因缺失快照自动退款、放行新付款或释放 UNKNOWN 占用。

集成复用 LoadUp FileResourceFacade 管理原文件与业务引用、TransferHandler/TransferContext 运行导入与报告任务、
生成 Mapper/MyBatis-Flex/MapStruct 管理本服务数据、DelayedTask 负责持久执行、Outbox 负责后续证据查证请求。
已有 Transfer 不代表核对业务已经接入；不能把 Transfer 的任务成功、最大已见事件时间或下载 404 当作对账完成证据。

当前 root reactor 注册实际已有的 client/domain 两层，源码/POM 边界门禁纳入 reconciliation。
后续补 app/infrastructure/adapter/rpc/bootstrap 时同步门禁和构建脚本，不用空接口宣称可运行服务。

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
源服务需生成可重放的 capture/export id、范围、完整性记录和不可变文件，证明查询窗口与截止快照一致；该源端导出用例仍待实现。

渠道下载经过已验签渠道 API 定位；人工上传时记录操作者和来源声明，文件哈希只证明内容没有变化，不证明文件真实性。
无账单可能是尚未生成或查询窗口错误；不能自动转换为完整空文件。双侧空输入只在取得各自明确完整零记录证据后封存。

导入器在事务外读取固定 FileResource，验证权限/来源、原字节哈希、解压/编码、账户、账期、所有记录、EOF 和尾部汇总。
未知格式/重复业务键/重复来源行序号/合计不符均拒绝整个输入，不跳过坏行后宣称成功。
文件处理有界输入与内存预算由后续应用配置控制；当前纯领域内核持有一个完整 bounded dataset，不声称已提供大文件流式导入能力。

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

| 方法 | 参数 | 语义 |
|---|---|---|
| create | requestKey、channelAccountCode、billDate | 由可信目录解析实际账户/profile，租户/调用方来自身份上下文 |
| attach | batchId、side、artifactId | 只引用认可导入/导出适配器签发的封存 artifact，不接受 raw URL、文件路径、complete=true 或客户端合计 |
| start | batchId | 本地登记原运行与持久任务；远端文件/源服务 I/O 不跨本地事务 |
| detail | batchId | 返回原输入摘要、状态、runId、版本与结果摘要 |
| differences | batchId、kind/reason、page/size | 查询本服务差错；默认 1/20，上限 100，租户/账户权限来自身份 |

这些目前只有 client 契约，不是已暴露 HTTP/RPC。后续 app 将直接实现 Facade；跨服务仅在提供方 rpc 模块定义 Protobuf。
不提供 UpdatePaymentStatus、直接退款、直接改额度或客户端提交“匹配成功结果”的接口。
差错复核/审批与 `SubmitEvidenceForVerification` 的实际可信源端查证、回执和授权仍需后续实施。

## 数据库与提交边界

[四表 SQL 草案](sql/reconciliation-draft.sql)仅在 design 下，不会被 Flyway 自动加载，未应用数据库。

| 表 | 责任与约束 |
|---|---|
| reconciliation_batch | 原 caller/requestKey 幂等、实际账户/账期/profile、双输入引用、原 runId、版本与结果 hash；同请求参数变化拒绝 |
| reconciliation_input | FileResource 引用、来源 capture、原/规范化哈希、原 parser revision、总额与 INIT/SEALED/REJECTED 导入状态 |
| reconciliation_entry | 每输入唯一业务键 hash 与唯一来源 ordinal，保存完整键防止哈希别名、原请求号、正数分/CNY 与规范化状态 |
| reconciliation_difference | 每批次唯一业务键，引用双侧不可变行并保存全部差异标志；单边记录和双边不一致的约束分开 |

DO 应继承 BaseDO；生成 Mapper/TableDef，不手写持久 Mapper/SQL 方法。SQL 草案不代表这些实现已经存在。
批次完成、差错集合和结果摘要需要本服务本地事务一起提交；重复任务须核对原 runId、输入与结果 hash。
只读/网络/文件处理与业务写入分开，所有时间仍使用系统本地 Clock/数据库审计时间。
不同账户、账期或 profile 不合并；不跨 payment/reconciliation schema 联表或写资金表。

## 当前证据与后续

已补纯领域测试源码：原支付与多次部分退款、处理中退款不冒充成功、全部不一致原因、双向单边、账户/日期/profile 隔离、
重复键/来源序号/错误合计/金额溢出/内容篡改、输入不可变、原运行与完成幂等。未执行。

待继续：可信账户目录/来源导出、真实账单解析与 File/Transfer 适配、本服务持久化与原子提交、应用/权限/公开适配、
任务与差错查证/复核、运行配置/构建/K8s，以及所有构建、单测、实际数据库和真实渠道/文件验收。
