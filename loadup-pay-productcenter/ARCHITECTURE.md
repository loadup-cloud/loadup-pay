# Product Center 架构

阶段 0 的[准入和额度发布时序](../design/PHASE0_FLOWS.md)及[依赖例外](../design/PHASE0_BASELINE.md)保留历史证据。[阶段 2](../design/PHASE2_IMPLEMENTATION.md)已在源码中拆开领域完整解析、条款投影与额度片段编译；不再以 INIT 的 ActivatedLimitPolicy 触发完整条款校验。源码和回归源码已同步，构建与测试未运行。

产品目录、条件、组合、销售方案、商户合约、条款解析以及支付准入和额度发布的权威服务。原 productcore 已合并到本应用。

目录发布、签约和合约状态变更在本地执行。暂停合约与新单准入继续使用同一数据库和合约锁。签约前通过独立服务凭证查询 merchantcenter 安全商户资料；不连接商户数据库。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前使用本地固定部署配置确定部署标识与 schema。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份，由 admin BFF 原样转发操作员 JWT；目标 center 独立校验 issuer、audience 和权限。admin/UPMS 登录与机器令牌签发由不同应用负责。当前两个 center 对 Admin issuer/audience 的信任配置仍需联调验证，不能以 BFF Controller 存在认定管理链路可用。

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 目录、发布与准入

```mermaid
flowchart LR
    Catalog["已发布目录与产品版本"] --> Contract["编译并保存商户合约修订"]
    Contract --> Publish["可靠发布初始额度"]
    Publish --> Limit["LimitCenter 激活策略"]
    Limit --> Ready["记录额度就绪"]
    Ready --> Admission["订单准入"]
    Merchant["商户资格与授权事实"] --> Admission
    Admission --> Terms["冻结订单条款与摘要"]
```

## 合约编译与持久化

- 读取productcenter 自有数据源中的 merchant_contract 和 merchant_contract_revision。当前签约只产生不可变 revision
  1，本适配明确支持该形态，不臆造在线改版接口。
- 从 revision 中要求恰好一个包含 trade.amountModel 参数的收单条目，并复用 ContractRuntimeResolver
  判断签约使用条件；不任意挑选多条收单能力中的第一条。
- 可信条件事实沿用商业条件字段 merchant.code/type/industry/country，并提供 merchant.id/active/province/city，以及
  transaction.amountMinor/currency。未知事实由合约解析器拒绝或判为不确定，不从商户 JSON 补充。
- 编译严格读取 CONTRACT-SCHEMA 的标量类型和已知路径，未知字段/枚举或不支持的 LIFO、自动迟到收款退款等策略拒绝。固定
  CNY、总额、全额计费、FIFO顺序退款语义不可配置绕过。
- AcquiringTermsDTO 新增 routingProfileRef、returnTargetCode，LimitPolicyRefDTO 新增 parentPolicyRef，避免编译丢失对应受控引用。两类
  hash 保持区分：原 snapshotHash 来自本地合约快照，compiledTermsHash 绑定 compilerVersion、contractId/revision、原 hash、typed条款与来源。
- 对 capability 组和 methods 集合排序后，使用本地 ContractCodec 的递归 map 规范化计算 SHA-256。私有 mapper 的 untyped
  数值转换使用 BigDecimal/BigInteger，不经过 double。compiled_terms 以 TEXT 保存应用产生的原始
  JSON；读取时再次校验身份、版本、hash与Bean Validation。
- 新增 order_admission、contract_limit_publication 实际 Flyway
  迁移。前者保存完整意图和商户/授权事实版本；后者由下述可靠发布工作流写入并供准入读取。合约表迁移归 productcenter，序列组件使用本应用冻结快照，设计SQL草案不能重复应用。
- READ_COMMITTED 本地短事务；同一合约等待锁后重读原 admission；orderId 唯一键兜底跨范围竞争。显式禁止本地外层事务包裹
  merchantcenter 网络调用。商户经营状态是读取到的可信事实，不承诺与 merchantcenter 状态变更的跨库全局零窗口。

materialize-seconds 默认300，允许1..86400；它是平台创建意图恢复窗口，不是商户订单寿命或存量续付期限。订单等待、延期、费率等仍取合约，不使用该配置代替商业条款。

## 初始额度可靠发布

| POST JSON 路径                            | Command/Query              | 权限                             |
|-------------------------------------------|----------------------------|----------------------------------|
| `/api/productmng/limit-publications/publish` | LimitPolicyPublishCommand  | `pay:product:publish`            |
| `/api/productmng/limit-publications/detail`  | IdQuery，id为publicationId | `pay:product:publication:read`   |
| `/api/productmng/limit-publications/replay`  | LimitPolicyReplayCommand   | `pay:product:publication:replay` |

上表为 admin BFF 路径，仅接受 OPERATOR；它将原操作者 JWT 转发至 `/api/internal/v1/productcenter/management/limit-publications/{publish,detail,replay}`。中心用例本身允许具备对应权限的 OPERATOR 或 SERVICE；SERVICE 要求 sub=service_id，部署标识取自本地固定配置，SERVICE 主体最长64字符。MERCHANT/MERCHANT_APP 即使误配发布权限也被拒绝。现有订单准入 JWT 契约与 merchantcenter 委托保持独立。

publish要求payeeMerchantId、productScopeKey、expectedContractId、expectedRevision=1、expectedSnapshotHash。它锁本地合约行，核对预期不可变快照，拒绝停止或已到期合约；尚未到生效时间可预先准备额度，订单准入仍检查原生效时间。额度从该revision唯一收单条目的冻结配置生成，不接受HTTP提交任意阈值/规则或“已经激活”。完整收单标量配置先通过 CommercialPolicyParser 的闭集与商业约束验证，再由 LimitPolicyCompiler 只消费额度片段生成命令。

首批固定contractRevision=1、policyVersion=1，每个商户只有一个初始发布绑定；同policy也不能给另一合约重新绑定。limit.policyRef必须是该商户独占且稳定的策略ID，不同商户不能共用同一个ID。当前不提供改版、重签迁移、多产品共享发布或父级聚合的管理协议。八个DAY/WEEK/MONTH/LIFETIME ×
AMOUNT/PAYMENT_COUNT规则始终存在，未启用的规则enforcement=false、metering=true，阈值未配时为0。stable
ruleScope只绑定协议v1和payee（UTF-8长度分帧SHA-256），不含policyId/version或contractId，避免换策略引用重新生成商户终生计数范围。后续规则升级须沿用稳定身份并走独立显式版本协议。

本地READ_COMMITTED事务中一起保存INIT意图、框架Task事件并更新为PUBLISHING；Task只带publicationId，不带令牌或完整商业载荷。原命令重试校验源contract/revision/hash/merchant/scope，返回原发布和event，不重发新意图；合约锁等待后重查，数据库唯一键作为最终约束。唯一键/瞬时冲突整事务回滚后最多重试3次。初始占位INIT不会单独提交。

框架Task提交后领取，处理器验证type/schemaVersion/businessId及本地event绑定，读取并重新校验冻结的激活命令/规范化hash。限额HTTP调用在本地事务之外，使用独立服务身份；不是转发发布请求的操作者JWT。只接受成功响应信封，且ACK的policyId/version/hash与原意图完全相同、激活时间有效，随后短事务CAS写ACTIVE。远端成功后、本地ACK提交前崩溃，可重投相同版本/hash获得原激活时刻；并发重复ACK幂等。ACK不更改本地合约经营状态，暂停/到期仍禁止新单。

本地state与deliveryStatus独立：state=PUBLISHING表示尚无可信ACK，即使Task已FAILED也不放行。detail返回队列状态及本轮次数，不暴露载荷/令牌。Task默认最多8次、5秒起指数退避、2分钟租约；失败后只有replay权限可带审核理由重放FAILED事件，沿用原ID/载荷并由框架记录操作者和原因。不会在详情查询中自动重试或伪造ACK。ACTIVE且投递完成前崩溃的重放跳过HTTP，直接让框架完成事件。

contract_limit_publication 保存来源字段、冻结载荷、Task 绑定、请求时刻和 publication_schema_version，并有每商户初始绑定及 Task 唯一约束。当前结构以本应用的 PostgreSQL 迁移为准，不将旧批次增量说明视为独立待执行脚本。若现有库含 schemaVersion=0 的历史记录，须先设计受控升级；不得直接改版本或 ACTIVE 状态伪造来源证据。

## 商业策略编译（阶段 2）

CommercialPolicyParser 位于 domain/policy，不依赖 Spring/Jakarta Validator 或公开 DTO。ValidatedCommercialPolicy 保留原 ResolvedConfiguration 和来源，包含订单、付款、费用、退款、通知、额度、渠道七类不可变片段。完整发布和新单准入都在原合约/资格/ACTIVE 检查位置调用统一解析，原幂等回放不进入新解析。

AcquiringTermsCompiler 只核对真实发布引用并投影条款，CommercialPolicyConverter 使用 runtime 的 MapStruct Spring 配置，原 Validator 保留为应用边界补充。LimitPolicyCompiler 只消费 LimitPolicy 与受信 tenant/payee，保留范围 ID、八组规则和 v1 哈希，不读其他片段或伪造激活。内部片段不写入公开快照；现有 client DTO、compilerVersion/schemaVersion、codec、存储与 acquire/payment 消费模型不变。

完整字段校验、通知参数分类、固定快照/hash 期望及待运行验收见[实施记录](../design/PHASE2_IMPLEMENTATION.md)。未进行构建、测试或真实 PostgreSQL 验证。
