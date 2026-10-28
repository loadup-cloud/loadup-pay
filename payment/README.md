# Reconciliation 后置交易对账

> 当前开发基线正切换至 PostgreSQL；各服务业务迁移已合并为单文件，RPC 协议位于各服务 client。下方历史验收描述保留其当时的数据库和运行状态；新基线尚需编译与真实 PostgreSQL 验证。

首期支付宝/微信官方交易日账单，按实际收款账户与账单日期核对收款、退款；银联待产品明确后接入。

当前已有 client/domain/app/infrastructure/adapter/rpc/bootstrap 七层源码：批次创建/封存输入引用、原运行与持久任务、
双侧完整输入核对、多原因差错、数据库适配和 HTTP/gRPC 批次入口。根 reactor、源码/POM 门禁及单应用构建/部署脚本已接入。
微信 ALL 交易账单解析源码按 2026-01-16 官方格式实现，独立校验账户、账期、规范化行和完整汇总；已接受信 SERVICE 的
File/Transfer 手工来源导入；官方自动下载与验签来源仍待接入。

所有新增源码未构建、运行测试、应用迁移或部署；不代表当前业务环境已可用。官方账单下载/验签来源、支付宝解析、
差错复核/受控查证仍待继续；payment 微信权威采集快照与完整导入已有源码，不直接改交易资金状态。

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

## P1 后置对账权威采集源码

新增 `PaymentCaptureFacade` 与提供方 `PaymentCaptureRpc/{Create,Detail,Lines,Retry}`，只允许配置的 reconciliation
SERVICE、可信租户和独立 capture 权限。
这里的 capture 指账务采集快照，与渠道资金授权/扣款无关。Create 同事务注册 INIT 元数据与原 DelayedTask，不在交易主链同步导出。
Worker 在有界只读 RR 内读取本库成功 claim/金融记录/原回执及独立退款受理时间；缺失覆盖证据、未决收款、时间冲突或超限明确
BLOCKED，绝不导出部分成功集合。
完整行与终态元数据同事务封存；后续分页只读封存表，原请求重放/FAILED
重试不覆盖首次结果。详见 [采集契约](../design/PAYMENT-RECONCILIATION-CAPTURE.md)。

业务配置：`PAYMENT_RECONCILIATION_SERVICE_ID` 默认 reconciliation、`PAYMENT_RECONCILIATION_MAX_RECORDS` 默认10000、
`PAYMENT_RECONCILIATION_READ_TIMEOUT_SECONDS` 默认10（1–60）。当前只支持微信固定 ALL profile，历史未决及缺退款日期可能保守阻断后续账期。
已补 Flyway/DO/MapStruct/生成 Mapper 消费、RPC/编排/领域及真实 MySQL 快照/回滚/退款日期测试源码，未运行构建/测试/DDL/渠道调用。

## 官方账单只读来源（P1，2026-10-28）

PaymentStatementFacade / PaymentStatementRpc.Download 只向配置的 reconciliation SERVICE 和独立读取权限提供原绑定范围账单。短
RR 获取不可变 revision 后，在事务外调用 ChannelStatementFacade；payment 不依赖厂商插件实现、不按当前路由换账户，也不复制凭证到
reconciliation。原件和私有来源证据有界传输且诊断隐藏，明确无账单/生成中错误，无商户 HTTP 下载入口。

该接口只读，不在 payment 封存来源任务/文件；reconciliation 的先登记意图、File/Transfer 与批次 CHANNEL 导入接线源码已补，尚未运行验证。消费需独立
deadline/20 MiB gRPC message cap。详见 [官方来源设计](../design/WECHAT-STATEMENT-SOURCE.md)。新增源码与测试未构建或执行。

## reconciliation 原对象只读查证

PaymentVerificationFacade / PaymentVerificationRpc.Verify 接收关联查询提示，仅允许配置的 reconciliation SERVICE 与 pay:
payment:reconciliation:verify。带预算只读 RR 按租户、原账户和原业务号定位唯一已提交付款或子退款，使用其冻结绑定事务外
query/queryRefund；不调用 Y2/Y3、提交、关闭或退款写操作。没有对象、未提交和不唯一是覆盖缺口，不能当作零金额/未收款证明。回包保留实际观察与原提示不一致、UNKNOWN
和可选退款受理时间。接口、索引与测试源码未构建或执行，见 [查证设计](../design/RECONCILIATION-VERIFICATION.md)。

## 2026-10-28 关单 ACK 独立保存

PaymentCloseService 已接原执行/恢复流程：只有已验证的 NO_COLLECTION/CLOSE_RESPONSE 才保存不可变的原 attempt 关单确认，独立提交后再交
Y3。Y3 失败后的原任务优先应用旧确认，避免把随后模糊的 CLOSED 当成新证据；没有收到或没有保存成功 ACK
时仍不制造未收款结论。独立表、MapStruct、原账户/epoch
防线与测试源码已补，未执行迁移或验证。详见 [恢复设计](../design/PAYMENT-CLOSE-RECOVERY.md)。
