# 微信官方交易账单来源（P1）

更新：2026-10-28。本文描述当前源码与仍待验收的生命周期；没有构建、测试或真实渠道验收结果。

## 范围与职责

首期范围为原实际收款账户的微信 ALL 交易日账单，固定 profile `wechat-trade-all-20260116`。
渠道下载只提供来源原件，不认定账单已解析、核对成功或资金状态发生变化。支付宝格式与下载适配另行实现，银联待产品明确。

- `ChannelStatementFacade` / `ChannelStatementProvider` 独立于付款与通知 SPI，复用 `ChannelStrategyRegistry` 按渠道选策略。
  缺少策略明确拒绝，重复注册启动失败；付款 Facade 不增加账单分支。
- `PaymentStatementFacade.download(PaymentStatementQuery)` 从可信租户读取原不可变 binding revision；读取使用短只读
  RR，结束后才调用渠道。
  停用当前路由不删除历史账户的读取能力；原 revision/账户不匹配时禁止网络调用，不切换到当前路由或其他凭证。
- `payment-client` 的 `PaymentStatementRpc.Download` 作为 Dubbo Triple IDL 契约，无商户 HTTP 下载入口。
  只允许配置的 reconciliation SERVICE 与 `pay:payment:reconciliation:statement:read` 权限；租户来自已验证 JWT。
  调用方不能提交 URL、渠道商户号、密钥引用或自报可信来源。
- reconciliation 负责冻结原来源意图、实际账户授权、File/Transfer、批次输入封存；相关应用、存储与任务接线源码已补。
  payment 不跨库读取 reconciliation，也不直接修改资金记录。

## 渠道请求与文件校验

1. 原 binding 的版本化凭证一次读取，申请和文件下载共用该份凭证；签名使用 KMS。
2. 签名 GET `/v3/bill/tradebill`，显式传 `bill_date` 与 `bill_type=ALL`，不传 `tar_type`。
   服务商模式必须带原 `sub_mchid`，禁止省略后下载全服务商账单；不支持的单子商户类型明确失败。
3. 原响应字节与 Wechatpay 签名头按原平台公钥/证书版本核验，之后才解析 `hash_type/hash_value/download_url`。
   只接受 SHA1 和 40 位十六进制。已签名的 `STATEMENT_CREATING`、`NO_STATEMENT_EXIST` 分别返回独立错误，不推导为空账单。
4. 下载地址只允许 HTTPS、默认端口或 443、官方主/备域名 `api.mch.weixin.qq.com` / `api2.mch.weixin.qq.com`、
   精确 `/v3/billdownload/file` 和唯一 token 参数；拒绝 userinfo、fragment、额外查询参数、重复 token、控制字符与路径变体。
   token 按值编码后检查最终实际 URI，再签名。下载不跟随重定向，不接受调用方任意目标地址。
5. 用固定命名客户端发起签名 GET；官方文件响应本身没有 Wechatpay 签名，必须校验原文件 SHA1 与已验签申请响应一致。
   仅接受 HTTP 200、非空原件和无压缩/identity；按调用上限写有界 OutputStream，默认能力上限 16 MiB，超限或校验失败不返回部分文件。
6. 申请返回的链接有效期为五分钟，应立即下载。代码不把本地接收时间当作渠道保证；过期/渠道拒绝继续作为失败处理。
   渠道账期使用官方 +08:00 日期边界，只允许已结束的账期，不修改 JVM/业务 Clock/数据库的系统本地时区。
   官方建议次日十点后获取，历史范围由渠道处理，源码不猜测“最近三个月”的精确日历算法。

`payment-wechat` 配置增加 `trade-bill` 和两个固定下载客户端。签名下载复用 LoadUp HTTP 的
`download(client, operation, call, output, signer)`，保持最终 URI/原字节签名、目标策略、大小限制、响应关闭及调用方输出流生命周期。
当前依赖的是同工作区 framework 的新源码 API，尚未构建或安装对应 artifact。

## 私有来源凭证与传输

`VerifiedChannelStatement` 包含原 binding revision、完整 scope、原文件和 `ChannelStatementReceipt`。
Receipt 含申请目标摘要、私有证据摘要、官方 SHA1、原文件 SHA256、接收与下载时间。
`requestSha256` **只对最终申请请求的 raw path + '?' + raw query 的 UTF-8 字节计算 SHA256**，不是整个 HTTP 请求的摘要。
`evidenceSha256` 对下面完整帧计算 SHA256；`contentSha256` 对未解压、未转码、未规范化的原文件字节计算 SHA256。

证据格式 `wechat-tradebill-grant-v1`：每字段先写四字节大端非负长度，再写原字节；字符串为 UTF-8。
字段依次为 format、tenantId、bindingRevisionId、channel、accountScopeHash、billDate、statementProfile、
credentialReference、credentialVersion、申请 requestTarget、申请 Authorization、响应 Wechatpay-Serial、
Wechatpay-Timestamp、Wechatpay-Nonce、Wechatpay-Signature、接收时间的 ISO Instant、原申请响应 body。
最后一字段保持原字节，其余保持原字符串；总证据上限 32 KiB、原 JSON body 上限 8 KiB。
它不是客户端可自行提交的可信证明，也不能仅凭摘要断言来源签名有效。

证据包含短期下载链接/令牌和渠道签名，仅限受权内部服务与私有对象存储。原文件和证据使用防御性复制，
`ToStringAsJson` 的 `DiagnosticHidden` 隐藏二者及凭证引用；不记录到日志、返回浏览器或拼进公开下载 URL。
DiagnosticHidden 是诊断规则，不能代替 HTTP 序列化权限；当前没有暴露该 DTO 的 HTTP Controller。

RPC 使用 optional 标量和 bytes、微秒 Timestamp；拒绝缺少字段、未知渠道/profile、空文件、超限、错误摘要、
官方 SHA1 不符或时间倒置。普通映射用 Spring MapStruct，ByteString 只做复制转换。
消费侧 `PaymentStatementFacade` 引用单独配置 60 秒超时，保留现有短事务 RPC 的预算。
完整响应上限为 16 MiB 原件加 32 KiB 证据及少量元数据，消费 channel 需显式允许至少 20 MiB inbound message。
消费端已配置独立 payment-statement channel 的 inbound.message.max-size=20MB 与默认 60s deadline，保留原 payment
短调用预算。配置及实际大报文、TLS、取消传播仍未验收。

接口是无资金副作用的读取，不提供服务端幂等封存：同一请求重复下载可能获得不同的签名申请凭证。
调用方必须先持久化原来源意图，在本库以首次有效提交绑定一个原件/凭证组合；RPC 回包丢失不应改为 MANUAL_UPLOAD。

## 官方 CHANNEL 来源生命周期（源码）

运营 API `/api/reconciliation/statements/{submit,detail,retry}` 对应 ReconciliationStatementFacade。
submit 只接受原 batchId/requestKey；渠道、账户、日期与 profile 来自原批次，bindingRevisionId 来自服务端账户目录的
`statement-binding-revision-id`。未配置该字段的账户仍可手工导入，但不能发起官方下载。
仅受信 SERVICE 和 `pay:reconciliation:statement:submit/read/retry`，原批次 caller 与当前账户授权必须一致。

1. 在原 batch 锁下核对同请求重放、范围、已结束账期、解析能力与活跃来源/导入。
   `reconciliation_statement_request` 的 INIT 行和原生 DelayedTask 在同 DataSource 事务中登记；没有先网络后补意图。
   原来源请求冻结 bindingRevisionId、实际 statement-account、scope、maxBytes、taskId 和参数摘要。
   配置以后轮换绑定不会重写在途原版本；当前账户授权/实际收款主体变更仍使处理失败。
2. 原生 `pay.reconciliation.statement` handler 校验可信 tenant、原 taskId/bizId/任务类型、RUNNING、attempts、执行时间与有效租约，
   在请求开始与提交前重新核对原任务。原子提交依赖业务行锁和 INIT/version 条件，不能将 ACK fencing 解释为文件 IO 只执行一次。
3. 事务外调用 PaymentStatementRpc，精确核验原 binding/scope、原件大小和 receipt 格式；DTO 对原件 SHA256/官方 SHA1/凭证
   SHA256 做一致性检查。
   网络失败、无账单和生成中保留 INIT，由原生失败/重试管理，不能转为零行成功。
4. 事务外通过 FileResource 保存两个私有文件：原始 CSV 与二进制来源凭证。每份文件的实际 size/SHA256 必须匹配 RPC 返回值。
   元数据表仅存文件快照、申请摘要、官方 SHA1 和协议时间，凭证原文不进业务表或浏览器。
5. 再锁原 batch 与来源请求，重新核对授权和任务，首次有效提交将以下内容同库原子落下：两个长期 File 引用、
   Transfer IMPORT 任务、`reconciliation_import` 的 OFFICIAL_DOWNLOAD 行、来源请求 READY/version=1。
   sourceId 与 importId 相同；下载 taskId 与 Transfer taskId 独立。内部导入键为 `official:<sourceId>`，手工 submit 禁用此保留前缀。
6. READY 表示下载与导入登记已持久化，导入行仍为 INIT；只允许后续 Transfer 的严格解析与输入/行/批次引用事务把它改为 SEALED。
   OFFICIAL_DOWNLOAD 在详情、重试、执行与封存前核对对应 READY 来源请求、完整原范围及两份永久引用。
   sourceKind 本身不是可信来源证明，普通手工上传继续为 MANUAL_UPLOAD。
7. 重复 worker、原生 ACK 丢失及 READY 后的 FAILED 重试只读取原文件/导入，不再次下载或替换凭证。
   如果其他输入已占用批次，尚未完成的来源请求转 SUPERSEDED/version=1，不覆盖已封存输入。
   手工导入在下载期间取得活跃位置时，本次下载提交失败并保留原请求，之后可在手工导入结束/拒绝后显式重试。

状态图：INIT → READY 或 SUPERSEDED；两种终态都不可回退。READY 的解析/核对结果由 import/batch 独立表达。
retry 必须带原 expectedTaskId，仅重试原 FAILED DelayedTask，RUNNING 不重领；Transfer 的失败重试仍使用原 imports/retry。
detail 为本地只读，不触发下载。没有自动重跑对账或修改资金记录的入口。

协议时间以绝对 epoch microseconds 保存，来自原始 Timestamp；数据库审计字段仍由 BaseDO 使用系统本地时区。
新增迁移 `V20261028000000__reconciliation_statement_source.sql`；前期直接调整 import 建表约束接受 OFFICIAL_DOWNLOAD，未运行
DDL。
官方来源最大 16 MiB，配置的 statement-max-bytes 需在 32 KiB–16 MiB，以容纳独立凭证；仍有实际大小与 EOF 校验。
消费配置 `payment-statement` 使用原 payment 地址、round_robin、独立 20MB 接收上限和可配置 60s deadline，逐次读取原挂载
SERVICE token。
配置字段依据 Dubbo Triple 的 `dubbo.protocol.triple.*` 与 `@DubboReference` 属性
，没有新增内部 HTTP 回退。

File IO 与本地事务不能合并；原件上传后凭证上传失败、数据库回滚或过期 worker 均可能遗留无永久引用对象。
首次有效提交只认原请求获胜的文件组合，临时/未附着资源的清理与保留策略仍待完善，不能为重试覆盖已引用文件。
当前尚无实际 MySQL 原子性、多 worker 租约或对象存储故障证据。

## 测试源码与证据

已补渠道策略路由/摘要/租户边界、真实 RSA 合成响应验签、原服务商/直连参数、主备目标、
错误签名/URL/缺失账单/超限/SHA1，payment 服务授权/原绑定/事务边界与 RPC presence/二进制/摘要测试源码。
LoadUp HTTP 增加签名流式下载的最终 URI/字节、调用方异常与输出流不关闭测试源码。均未执行。
来源请求状态、元数据 nullable/epoch 映射、下载编排/原任务重试/租约、官方来源防伪关联测试源码已补；原 File/Transfer IT
装配同步。新增来源链路的专用 MySQL 回滚/并发与真实 RPC 故障用例仍待补齐。
尚无真实渠道凭证、对象存储、JWT/TLS/RPC、数据库、集群任务或端到端核对成功证据。

官方依据：[交易账单申请（直连）](https://pay.weixin.qq.com/doc/v3/merchant/4013071227.md)、
[交易账单申请（服务商）](https://pay.weixin.qq.com/doc/v3/partner/4012760667.md)、
[下载账单](https://pay.weixin.qq.com/doc/v3/merchant/4013071238.md)、
[交易账单格式](https://pay.weixin.qq.com/doc/v3/merchant/4013071246.md)。
