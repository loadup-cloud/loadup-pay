# product 架构

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 合约编译与持久化

- 读取同一 product 数据源中的框架 merchant_contract 和 merchant_contract_revision。当前框架签约只产生不可变 revision
  1，本适配明确支持该形态，不臆造在线改版接口。
- 从 revision 中要求恰好一个包含 trade.amountModel 参数的收单条目，并复用 ContractRuntimeResolver
  判断签约使用条件；不任意挑选多条收单能力中的第一条。
- 可信条件事实沿用框架 merchant.code/type/industry/country，并提供 merchant.id/active/province/city，以及
  transaction.amountMinor/currency。未知事实由框架拒绝或判为不确定，不从商户 JSON 补充。
- 编译严格读取 CONTRACT-SCHEMA 的标量类型和已知路径，未知字段/枚举或不支持的 LIFO、自动迟到收款退款等策略拒绝。固定
  CNY、总额、全额计费、FIFO顺序退款语义不可配置绕过。
- AcquiringTermsDTO 新增 routingProfileRef、returnTargetCode，LimitPolicyRefDTO 新增 parentPolicyRef，避免编译丢失对应受控引用。两类
  hash 保持区分：原 snapshotHash 来自框架，compiledTermsHash 绑定 compilerVersion、contractId/revision、原 hash、typed条款与来源。
- 对 capability 组和 methods 集合排序后，使用框架 ContractCodec 的递归 map 规范化计算 SHA-256。私有 mapper 的 untyped
  数值转换使用 BigDecimal/BigInteger，不经过 double。compiled_terms 以 LONGTEXT 保存应用产生的原始
  JSON；读取时再次校验身份、版本、hash与Bean Validation。
- 新增 order_admission、contract_limit_publication 实际 Flyway
  迁移。前者保存完整意图和商户/授权事实版本；后者由下述可靠发布工作流写入并供准入读取。框架表及序列仍由框架迁移维护，设计SQL草案不能重复应用。
- READ_COMMITTED 本地短事务；同一合约等待锁后重读原 admission；orderId 唯一键兜底跨范围竞争。显式禁止本地外层事务包裹
  customer 网络调用。商户经营状态是读取到的可信事实，不承诺与 customer 状态变更的跨库全局零窗口。

materialize-seconds 默认300，允许1..86400；它是平台创建意图恢复窗口，不是商户订单寿命或存量续付期限。订单等待、延期、费率等仍取合约，不使用该配置代替商业条款。

## 初始额度可靠发布

| POST JSON 路径                            | Command/Query              | 权限                             |
|-------------------------------------------|----------------------------|----------------------------------|
| `/api/product/limit-publications/publish` | LimitPolicyPublishCommand  | `pay:product:publish`            |
| `/api/product/limit-publications/detail`  | IdQuery，id为publicationId | `pay:product:publication:read`   |
| `/api/product/limit-publications/replay`  | LimitPolicyReplayCommand   | `pay:product:publication:replay` |

这三个管理入口仅接受签名JWT中的OPERATOR或SERVICE；SERVICE要求sub=service_id，tenant_id禁止首尾空白，主体最长64字符。独立权限不会由商户角色继承；MERCHANT/MERCHANT_APP即使误配发布权限也被拒绝。现有订单准入JWT契约与customer委托保持独立。

publish要求payeeMerchantId、productScopeKey、expectedContractId、expectedRevision=1、expectedSnapshotHash。它锁框架合约行，核对预期不可变快照，拒绝停止或已到期合约；尚未到生效时间可预先准备额度，订单准入仍检查原生效时间。额度从该revision唯一收单条目的冻结配置生成，不接受HTTP提交任意阈值/规则或“已经激活”。完整收单标量配置必须通过既有编译器验证。

首批固定contractRevision=1、policyVersion=1，每个商户只有一个初始发布绑定；同policy也不能给另一合约重新绑定。limit.policyRef必须是该商户独占且稳定的策略ID，不同商户不能共用同一个ID。当前不提供改版、重签迁移、多产品共享发布或父级聚合的管理协议。八个DAY/WEEK/MONTH/LIFETIME ×
AMOUNT/PAYMENT_COUNT规则始终存在，未启用的规则enforcement=false、metering=true，阈值未配时为0。stable
ruleScope只绑定协议v1、可信tenant和payee（UTF-8长度分帧SHA-256），不含policyId/version或contractId，避免换策略引用重新生成商户终生计数范围。后续规则升级须沿用稳定身份并走独立显式版本协议。

本地READ_COMMITTED事务中一起保存INIT意图、框架Outbox事件并更新为PUBLISHING；Outbox只带publicationId，不带令牌或完整商业载荷。原命令重试校验源contract/revision/hash/merchant/scope，返回原发布和event，不重发新意图；合约锁等待后重查，数据库唯一键作为最终约束。唯一键/瞬时冲突整事务回滚后最多重试3次。初始占位INIT不会单独提交。

框架Outbox提交后领取，处理器验证type/schemaVersion/tenant/businessId及本地event绑定，读取并重新校验冻结的激活命令/规范化hash。限额HTTP调用在本地事务之外，使用独立服务身份；不是转发发布请求的操作者JWT。只接受成功响应信封，且ACK的policyId/version/hash与原意图完全相同、激活时间有效，随后短事务CAS写ACTIVE。远端成功后、本地ACK提交前崩溃，可重投相同版本/hash获得原激活时刻；并发重复ACK幂等。ACK不更改框架合约经营状态，暂停/到期仍禁止新单。

本地state与deliveryStatus独立：state=PUBLISHING表示尚无可信ACK，即使Outbox已FAILED也不放行。detail返回队列状态及本轮次数，不暴露载荷/令牌。Outbox默认最多8次、5秒起指数退避、2分钟租约；失败后只有replay权限可带审核理由重放FAILED事件，沿用原ID/载荷并由框架记录操作者和原因。不会在详情查询中自动重试或伪造ACK。ACTIVE且投递完成前崩溃的重放跳过HTTP，直接让框架完成事件。

新迁移V20261021200000为contract_limit_publication增加来源字段、冻结载荷、Outbox绑定、请求时刻和publication_schema_version，以及每商户初始绑定/Outbox唯一键。旧迁移保持原样；既有行保留schemaVersion=0，既不删除也不补造源信息，准入不再信任其ACTIVE。遗留记录须后续受控迁移，不通过直接改schemaVersion/ACTIVE解决。
