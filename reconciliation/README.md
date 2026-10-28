# Reconciliation 后置交易对账

首期支付宝/微信官方交易日账单，按实际收款账户与账单日期核对收款、退款；银联待产品明确后接入。

当前已有 client/domain/app/infrastructure/adapter/rpc/bootstrap 七层源码：批次创建/封存输入引用、原运行与持久任务、
双侧完整输入核对、多原因差错、数据库适配和 HTTP/gRPC 批次入口。根 reactor、源码/POM 门禁及单应用构建/部署脚本已接入。
微信 ALL 交易账单解析源码按 2026-01-16 官方格式实现，独立校验账户、账期、规范化行和完整汇总；已接受信 SERVICE 的
File/Transfer 手工来源导入；微信官方下载与 payment 只读 RPC 已有源码，本服务官方来源请求/任务/File/Transfer 接线源码已补。

所有新增源码未构建、运行测试、应用迁移或部署；不代表当前业务环境已可用。官方来源真实环境验收、支付宝解析、
差错独立复核/审批源码已补，显式可信源端只读查询/留档源码已补，受控金融处置仍待继续；payment 微信权威采集快照与完整导入已有源码，不直接改交易资金状态。

## 业务装配

- 构建入口 `./reconciliation/build.sh -Preconciliation-business`；运行时启用 `reconciliation-business`。
- 独立 `RECONCILIATION_JDBC_URL/USERNAME/PASSWORD`、`RECONCILIATION_JWT_ISSUER_URI/AUDIENCE/JWK_SET_URI`。
- 导入与比较上限 `RECONCILIATION_MAX_RECORDS` 默认 10000；解析原字节上限 `RECONCILIATION_STATEMENT_MAX_BYTES` 默认 16
  MiB。
- DelayedTask 同库注册/执行，默认每轮 10 条、最大 5 次、租约 5 分钟、重试间隔 1 分钟；Local Scheduler 每 30 秒唤醒。
- 默认账户目录为空，拒绝全部账户操作；外部配置 `loadup.modules.reconciliation.accounts.tenants.<tenant>.<code>`，包括
  channel、account-scope-hash、statement-profile、实际 statement-account 与明确 service-ids。目录不存渠道密钥，不接受请求传入账户
  hash。
- 仅已验证的 SERVICE 身份，sub 必须等于 service_id；租户来自 JWT。所需权限分别是
  `pay:reconciliation:batch:create/attach/start/read`。
  原批次只允许原 caller 及当前账户授权读取/操作；商户凭证不能直接取得对账运营权限。
- HTTP POST `/api/reconciliation/batches/{create,attach,start,detail,differences}`；start 使用
  `ReconciliationBatchStartCommand`。
  gRPC `ReconciliationRpc/{Create,Attach,Start,Detail}`，提供方拥有 Protobuf，复用框架入站 JWT 与错误映射。
  differences 使用通用 PageDTO/PageResponse，默认 1/20，最大 100；终态差错查询不回源交易服务。

以下外部配置仅示意结构；hash 应由受控账户身份规范计算，不直接使用示例值：

```yaml
loadup:
  modules:
    reconciliation:
      accounts:
        tenants:
          merchant-tenant:
            wechat-account:
              channel: WECHAT
              account-scope-hash: <actual-account-scope-sha256>
              statement-profile: wechat-trade-all-20260116
              statement-binding-revision-id: <original-payment-binding-revision-id>
              statement-account:
                merchant-id: '1234567890'
                # sub-merchant-id: '1234567899' # Set for the actual provider sub-merchant.
              service-ids: [ pay-ops ]
```

本服务只有经受信导入/导出适配器完整验证并封存的 artifact 才能 attach。普通文件上传不是封存证明；当前不开放直接上传规范化行或成功结果。
微信解析器不能证明下载来源真实性；不支持的 REVOKED 明细明确拒绝整份输入，等待独立撤销补偿身份接入，不会被跳过。

## 数据与执行

五张业务表和 Flyway 迁移源码位于 infrastructure，DO 继承 BaseDO，Mapper/TableDef 由处理器生成。
create/attach/start 是独立本地写事务；start 的原 runId 与原生任务同事务提交。执行按“只读 RR 取封存输入 → 事务外纯比较 →
本地写事务锁批次并提交全部差错/完成摘要”分段。
原生领取/租约/ACK 由 DelayedTask 管理；批次原运行和确定性结果保证业务幂等，不把元数据检查称为原生 token fencing。
同一批次不能换源重跑；更正文件/profile 应新建输入和批次。查询与计数处于同一次只读 RR 快照。

`deploy/pay.yml` 的第七组清单仍是骨架，业务镜像需额外准备 Secret/配置覆盖层；健康检查或 DNS 就绪不代表真实对账通过。
本轮没有执行构建、脚本、数据库或外部金融调用。详见 [对账设计](../design/RECONCILIATION.md)、[SQL](../design/sql/reconciliation-draft.sql)。

## 手工渠道文件导入

当前支持微信 API ALL 格式的原始 UTF-8 文本文件（非 xlsx、ZIP 或 GZIP）。入口是 POST /api/reconciliation/imports：

| 路径    | 输入与权限                                                                  | 结果                                                                        |
|---------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| /upload | multipart file；pay:reconciliation:import:upload                            | FileResource id、实际字节数、SHA256；不返回 storageId、存储地址或密钥       |
| /submit | batchId、requestKey、fileId、sourceSha256；pay:reconciliation:import:submit | 原导入、原任务和 MANUAL_UPLOAD 来源标记                                     |
| /detail | IdQuery；pay:reconciliation:import:read                                     | 逻辑 INIT/SEALED/REJECTED、原 artifact、独立 native taskStatus 与固定拒绝码 |
| /retry  | importId、expectedTaskId；pay:reconciliation:import:retry                   | 仅原 FAILED 重排/原 QUEUED 重放；不替换文件或重领 RUNNING                   |

sourceSha256 必须是上传返回的原字节 SHA256，用于绑定选择的文件，不证明渠道真实性。原请求重放不重新读取源文件；变更批次/文件/hash
冲突。
原租户/调用方来自已验证 JWT，调用方必须同时是文件拥有者、批次原 caller，且仍有当前账户目录授权。
实际账单商户/子商户标识由目录 statement-account 冻结，不能由请求参数传入；目录变更后不重解释原导入。
批次锁串行限制同一批次一个 INIT 渠道导入；已有渠道输入或运行/完成批次不能新导入。

提交先在事务外读取并固定原文件 id/storageId/长度/SHA256，再在同库事务登记导入、业务文件引用和原 Transfer 任务。
任务绑定 tenant/owner/taskId/handler/sourceFileId 和唯一 importId option；只读 RR 查证原任务为 RUNNING，解析在事务外。
接收完整原长度并核对 SHA256 后才判断账单内容；I/O/短读为依赖失败，保留 INIT 等待原 FAILED 显式重试。
完整字节与原 hash 不符、格式/汇总/作用域不符则 REJECTED，不写部分行或伪造空文件。
封存输入/全部行、批次 CHANNEL 引用和导入 SEALED 在一个本地写事务提交。原任务 ACK 丢失或报告失败不撤销封存，重放核对原
manifest 后跳过解析。

MANUAL_UPLOAD 明确记录于导入审计，capture id 对应原 import id；在 /imports/detail 查看来源。普通上传不升级为渠道验签下载，不开放
LEDGER CSV 导入。
长期业务文件引用在成功或拒绝后均保留，原生 Transfer 释放临时引用不使证据文件可删除；保留/清理政策仍待后续实施。
Transfer 当前没有租约恢复；崩溃遗留 RUNNING 不自动改成 QUEUED，逻辑是否已封存可独立查询。需后续补受控恢复与运行证明，不能声称已覆盖崩溃恢复。

业务 profile 新增 LoadUp Transfer/File 本地装配与 S3 binder，元数据和业务表共用本服务 DataSource，不引入内部 HTTP。
需提供私有 RECONCILIATION_S3_BUCKET，可配置 REGION/ENDPOINT/PATH_STYLE；使用默认凭证链或一对
S3_ACCESS_KEY/S3_SECRET_KEY，不自动创建 bucket。
Local Scheduler 同时唤醒 Transfer 与比较任务；默认每轮 10 个导入，multipart 上限默认 16 MiB/请求 17 MiB，可配置
IMPORT_MAX_REQUEST_SIZE。
本轮仅写源码/测试/迁移/配置，未连接 S3、数据库，未运行任何命令验收。

## payment 权威采集快照（P1 源码）

当前仅 WECHAT / `wechat-trade-all-20260116`。使用 [采集契约](../design/PAYMENT-RECONCILIATION-CAPTURE.md)，
调用原 payment 的 gRPC；不读 realtime，不跨数据库，不接受上传 LEDGER CSV。

- `POST /api/reconciliation/ledger/prepare`：batchId/requestKey；按原批次 scope 请求异步源端快照，重复 requestKey 查询原结果。
- `POST /api/reconciliation/ledger/import`：batchId/captureId；只接受 SEALED，完整分页/空 EOF/总计/hash/冻结范围核对后，
  上传原规范二进制证据，再同事务保存文件长期引用、LEDGER 全行和批次关联；重复原 capture 不做远端 IO。
- `POST /api/reconciliation/ledger/retry`：batchId/captureId/expectedTaskId；只重试源端原 FAILED 任务，不能刷新已封存结果。
- 独立运营权限 `pay:reconciliation:ledger:prepare/import/retry`，仍要求原 caller 与当前账户目录授权。
- 出站服务凭证为 `RECONCILIATION_SERVICE_ID`（默认 reconciliation），配置 `RECONCILIATION_PAYMENT_TENANT_ID`、
  `RECONCILIATION_PAYMENT_AUDIENCE`（默认 payment）和 `RECONCILIATION_PAYMENT_TOKEN_FILE`。当前凭证配置对应一个租户，其他租户显式拒绝。
  接收方需授予 `pay:payment:reconciliation:capture:create/read/retry`；逐次读取挂载凭证，未签发真实令牌。
- 默认 `dns:///payment-headless:50051` + round_robin，每 RPC 3 秒，完整抓取预算 60 秒、分页100。
  `RECONCILIATION_PAYMENT_GRPC_TARGET/RPC_DEADLINE` 与 `RECONCILIATION_LEDGER_FETCH_SECONDS` 可配置；TLS 沿用 Spring
  gRPC。
- INIT/BLOCKED 不能导入；BLOCKED 是覆盖阻断样本，无可导出金额/行。查证原资金后使用新 requestKey 请求新 RR，原快照永不覆盖。

账务源生成和对账挂入是显式两步，当前不自动轮询/自动导入或启动核对。导入数据库失败可重放原 capture，
上传后尚未登记的文件按 File 的无引用资源政策处理，长期证据不自动清理。上述源码/测试未编译运行或联调。

## 官方 CHANNEL 来源（P1，2026-10-28）

`POST /api/reconciliation/statements/{submit,detail,retry}` 提供受信服务运营入口，独立权限
`pay:reconciliation:statement:submit/read/retry`。
submit 仅传 batchId/requestKey，账户目录显式配置 `statement-binding-revision-id`；未配置仍允许手工导入，但不能官方下载。
原请求与 DelayedTask 先同库落 INIT，再由原生 handler 事务外调用 payment 的原绑定只读 RPC、保存原件/凭证。
两个长期 File 引用、Transfer、OFFICIAL_DOWNLOAD import 与来源 READY 原子登记；原严格解析随后封存批次 CHANNEL。
READY 的 sourceId 就是 importId；下载 taskId 与 Transfer taskId 独立，重试分别沿用原 expectedTaskId，不重领 RUNNING。

payment-statement 独立 channel 使用原 payment 目标、round_robin、20MB inbound 上限及默认 60s deadline；服务凭证需增加
payment 官方来源读取权限。
原 scope/实际收款主体/当前授权每次核验，绑定版本冻结，配置轮换不会换掉原来源；原件和凭证不提供公开链接。
明细/重试/封存均校验 READY 来源行及长期引用，sourceKind 不能自行声明为可信来源。
内部 import key 保留 `official:` 前缀，手工 submit 禁用。statement-max-bytes 需 32KiB–16MiB。

来源表为 reconciliation_statement_request；原件/凭证留 FileResource，不将 URL/令牌原文放进业务表。
未附着对象清理、来源专用 MySQL 原子性/并发测试、支付宝解析、真实查询验收及受控金融处置仍待完成。新源码、迁移和测试均未运行。
详见 [来源契约](../design/WECHAT-STATEMENT-SOURCE.md)。

## 差错独立复核（P1）

`/api/reconciliation/reviews/{evidence,propose,decide,detail,history}` 提供私有证据上传、不可变提案与独立 SERVICE
审批、当前详情和追加审计。原 DIFFERENCES 结果、差错和资金不被复核改写。新权限为 pay:reconciliation:review:
upload/propose/decide/read；独立审批服务必须获得相同账户目录授权，且身份与原提案提交服务不同。这不构成两名自然人审批证明，也不授予修改原批次的权限。

未提案时为虚拟 INIT0；原子提案后 PROPOSED1，独立审批为 APPROVED2 或 REJECTED2，驳回可在当前版本重新提案。原提案和命令回执不覆盖，重放返回原
ACK，当前状态使用 detail。备注诊断隐藏，File 证据永久引用与 proposal/event/head 同库提交。三表迁移、领域/应用/映射和真实
MySQL 故障注入/并发 IT 测试源码已补，均未运行。详见 [复核设计](../design/RECONCILIATION-REVIEW.md)
；源端只读查询/观察留档源码已补，实际验收与金融修正仍待继续。

## 获批差错的显式源端查询

`/api/reconciliation/verifications/{submit,detail,retry}` 只允许 verification 独立权限、当前账户授权、原
APPROVED/NEEDS_SOURCE_VERIFICATION 提案及精确审批事件/版本。请求与原 Outbox 先同库冻结，服务器选取原自然键和优先 LEDGER
的原请求号。原 SourceQuery 只从 payment 已提交对象重建原绑定，事务外只读查询原渠道；无对象/未提交/不唯一返回明确覆盖缺口，UNKNOWN
不变成未收款。

源回执与原请求完全关联后首次有效提交独立留档，RECORDED 不等于差错解决；ACK 丢失或原 FAILED
重放不再覆盖回执/重复查询。收款/退款金额、结果与批次/复核均无写入口。复用框架 Outbox、60s 的独立 payment-verification gRPC
channel 与原 SERVICE Token（需新增 payment 读取权限）。两侧 Client
API/持久化/投递/Proto/MapStruct/索引迁移和测试源码已补，均未运行。见 [查证设计](../design/RECONCILIATION-VERIFICATION.md)。
