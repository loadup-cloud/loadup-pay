# Reconciliation 后置对账

比较同账户、账期下的完整封存 CHANNEL 与 LEDGER 输入，记录差错和独立复核；不裁定支付终态或直接修改资金。当前支持微信 ALL 交易账单，支付宝解析及金融处置未完成。来源、封存和任务规则见 [ARCHITECTURE](ARCHITECTURE.md)。

## 接入配置

启动模块为 `loadup-pay-reconciliation-bootstrap`，HTTP 默认 8080，local profile 为 8087。配置见 [application.yml](loadup-pay-reconciliation-bootstrap/src/main/resources/application.yml)。

| 配置 | 用途/默认值 |
|---|---|
| `RECONCILIATION_JDBC_URL/USERNAME/PASSWORD` | 独立 PostgreSQL，业务、File、Transfer、任务共用数据源 |
| `JWT_ISSUER_URI`、JWKS/公钥 | 入站 audience 为 reconciliation |
| `SERVICE_AUTH_TOKEN_URI/CLIENT_SECRET` | 出站 payment 的 OAuth2 SERVICE 凭证 |
| `RECONCILIATION_S3_BUCKET` | 私有证据 bucket；不会自动创建 |
| `RECONCILIATION_S3_REGION/ENDPOINT/PATH_STYLE/ACCESS_KEY/SECRET_KEY` | S3 配置或默认凭证链 |
| `RECONCILIATION_MAX_RECORDS` | 最大记录数，默认 10000 |
| `RECONCILIATION_STATEMENT_MAX_BYTES` | 原账单最大 16 MiB，允许范围 32 KiB–16 MiB |
| `RECONCILIATION_LEDGER_FETCH_SECONDS` | 完整快照获取预算，默认 60 秒 |

账户目录默认空、拒绝全部操作。配置 `loadup.modules.reconciliation.accounts.tenants.<tenant>.<code>` 的 channel、account-scope-hash、statement-profile、实际 statement-account、service-ids；官方下载还需 statement-binding-revision-id。hash 由受控账户身份计算，不接受请求自报。

仅 SERVICE 且 sub=service_id；部署标识来自本地固定配置，必须有当前账户授权。原 caller 约束不因令牌轮换或账户配置变更被绕过。

## 操作入口

| POST 路径组 | 操作与权限前缀 |
|---|---|
| `/api/reconciliation/batches/` | create/attach/start/detail/task/retry/differences；`pay:reconciliation:batch:*` |
| `/api/reconciliation/imports/` | upload/submit/detail/retry；`pay:reconciliation:import:*` |
| `/api/reconciliation/ledger/` | prepare/import/retry；`pay:reconciliation:ledger:*` |
| `/api/reconciliation/statements/` | submit/detail/retry；`pay:reconciliation:statement:submit/read/retry` |
| `/api/reconciliation/reviews/` | evidence/propose/decide/detail/history；`pay:reconciliation:review:upload/propose/decide/read` |
| `/api/reconciliation/verifications/` | submit/detail/retry；独立 verification 权限 |

内部六个批次契约位于 `/api/internal/v1/reconciliation/batches/`，复用相同 Facade 与认证。差错分页，终态查询不回查交易源。批次 retry 要求原 batchId/runId/expectedTaskId，仅重试原 FAILED 任务。

手工 upload 为 multipart UTF-8 微信 ALL 原文，不接受 xlsx/ZIP/GZIP 或 LEDGER CSV。submit 使用原 batchId/requestKey/fileId/sourceSha256；hash 只绑定文件，不证明官方来源。

官方 submit 只传 batchId/requestKey，由目录冻结原绑定；ledger prepare/import 为显式两步，不自动轮询、导入或启动比较。INIT/BLOCKED 快照不可导入；修复源事实后用新 requestKey 请求新快照，原 capture 不覆盖。

出站 payment 默认 `http://payment:8080`；普通组使用框架默认，`payment-statements` 使用 60 秒、32 MiB 响应和独立 8/4 连接池，页大小 100。查证使用独立 `payment-verification` 超时预算；目标服务验证最小权限。

## 限制与待验收

只支持 WECHAT/`wechat-trade-all-20260116`，不支持的 REVOKED 明细拒绝整份输入；手工上传不升级为官方证明。Transfer 无崩溃租约回收，RUNNING 不自动重领；文件保留/清理、支付宝解析和金融修正仍待完善。

待验收：PostgreSQL 封存/比较/回执的原子性与并发，File/Transfer/S3/JWT/HTTP 真链路、官方来源验真、源端采集与分页完整性、任务中断/重试、独立复核授权及只读查证。入口见 [测试说明](loadup-pay-reconciliation-infrastructure/src/test/README.md)。

## 子模块入口

| 工件职责 | 接入 | 设计 |
| --- | --- | --- |
| 客户端契约 | [README](loadup-pay-reconciliation-client/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-client/ARCHITECTURE.md) |
| 启动装配 | [README](loadup-pay-reconciliation-bootstrap/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-bootstrap/ARCHITECTURE.md) |
| Web 适配 | [README](loadup-pay-reconciliation-adapter/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-adapter/ARCHITECTURE.md) |
| 应用编排 | [README](loadup-pay-reconciliation-app/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-app/ARCHITECTURE.md) |
| 领域模型 | [README](loadup-pay-reconciliation-domain/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-domain/ARCHITECTURE.md) |
| 基础设施 | [README](loadup-pay-reconciliation-infrastructure/README.md) | [ARCHITECTURE](loadup-pay-reconciliation-infrastructure/ARCHITECTURE.md) |
