# LoadUp Pay 架构收敛与实施计划

> 状态：草案（待评审）。日期：2026-11-07。
> 适用：`loadup-pay` + `loadup-framework` + `loadup-admin`。
> 本文把上一轮架构评审的意见与后续确认的 5 点说明合并，形成可执行方案；与 [ROADMAP.md](../ROADMAP.md) 冲突处以本文为准，最终回写到 ROADMAP。

## 0. 结论速览

目标形态从 **11 个可部署单元收敛到 10 个**：

| 分类 | 应用 | 说明 |
|---|---|---|
| 支付核心（7） | acquire、payment、limitcenter、realtime、reconciliation、merchantcenter、productcenter | 保持现状，边界不动 |
| 通知 | notificationcenter | **保留独立服务**：站内信 + Gotone 引擎承载 SMS/第三方渠道投递，当前未接正式渠道 |
| 身份/令牌 | oauth2-server（由 authserver 改名，见 D3） | 商户/应用（client_credentials）令牌签发 + 瘦身后的内部服务令牌 |
| 运营管理台 | admin（位于 `loadup-pay/admin`，新建单体 bootstrap） | 合并 merchantmng + productmng，内嵌 UPMS 与框架业务模块 |

### 0.1 已确认的补充约束

1. **所有应用不对外暴露，所有入口统一走 Gateway 路由**；内部 `/api/internal/**` 与 Actuator 永不进网关路由。公开路由与后端映射如下（服务自身无公网 LoadBalancer/Ingress）：

   | 网关前缀 | 后端 | 说明 |
   |---|---|---|
   | `/api/acquire`、`/api/cashier`、`/checkout`、`/checkout.js` | acquire | 商户/收银台 |
   | `/api/admin` | admin | UPMS 登录 + 商户/产品管理 |
   | `/api/notifications/**` | notificationcenter | 操作员站内信读接口 |
   | `/api/payment/channel-callbacks`、`/api/payment/refunds` 等 | payment | 渠道回调与受控公开操作 |

   内部调用（service → service）走集群内 ClusterIP DNS，不经网关。
2. **Notification Center 保留**：它是独立通知投递服务（站内信 + SMS），承载支付链路的短信通知；不是管理后台的一部分，当前只是尚未接入正式渠道。
3. **AuthServer 改名**：定为 `oauth2-server`（已决，理由见 D3）。

五个关键决策对应下文的 D1–D5。整体原则：**先消除“半成品”和“假安全”，再谈解耦与平台化；每一步都带可运行的验证。**

---

## 1. 决策记录

### D1 · 多租户：当前按“单租户部署”处理，SaaS 只留接口不实现

**结论**：`loadup-pay` 部署一套 = 一个租户。schema-per-tenant 与运行时路由**本阶段不做**，但保留廉价的设计钩子；等真正做 SaaS 时再启用，避免现在写出一套“看似多租户、实际只服务 default”的假隔离。

**落地**：

1. 把 `loadup.database.multi-tenant` 改为显式单租户：
   - `enabled=false`（或引入 `mode=single-tenant`），移除 `required=true`；
   - `DatabaseTenant` 不再把租户 ID 直接解释为 schema，改为固定解析到配置的默认 schema；
   - 请求若携带非默认租户 → 拒绝（fail-fast），而不是静默落到 public。
2. 保留的钩子（零成本、不写死）：
   - JWT 仍携带逻辑 `tenant_id`（默认 `__default__`），`TenantContext`/`ExecutionContext` 继续存在；
   - 业务表继续不写 `tenant_id` 列；Redis key、DFS 路径、RPC 身份继续带租户命名空间。
3. 写一条 ADR（`design/adr/0001-single-tenant-deploy.md`）记录：“schema-per-tenant + 连接路由 + 分 schema Flyway = deferred；启用条件 = SaaS 化明确立项”。

**验收**：所有服务在 `enabled=false` 下启动、跑通迁移；传非默认租户被拒绝并记录日志；无任何代码依赖“多租户已生效”。

---

### D2 · 契约耦合：契约隔离 + 兼容性策略，而不是“所有 DTO 放一块”

**先回答“放一块是不是还得改所有应用”**：改一处要改所有应用的根因不是“DTO 放在哪”，而是三件事叠加：

1. **传递依赖**：现在 `realtime-client → acquire-client → payment-client → productcenter-client`，且 client 还携带本服务 `domain`，所以改 payment 一个类，会连锁重编译 realtime/acquire。
2. **破坏性变更**：重命名/删除/改变字段语义，任何共享都会要求所有消费方改逻辑。
3. **锁步发布**：`up.py all` 整组发布，改一处就必须全体重新构建。

所以“放一块”只解决“重复定义”，不解决“传播”。真正解法是下面的四条。

**目标模块结构**：

```text
loadup-pay/
├── pay-contracts/                  # 中性契约模块：无 Spring、无 domain、无其它服务依赖
│   ├── pay-contracts-events/       # 跨服务事件 DTO（payment/refund/order/…快照与结果事件）
│   └── pay-contracts-common/       # 可选：EventEnvelope 之外的跨服务信封/错误码（或留在 commons）
├── acquire/acquire-client/         # 只含 acquire 自己的 HTTP 入参/DTO/Facade
├── payment/payment-client/         # 只含 payment 自己的 HTTP DTO；事件 DTO 移到 pay-contracts-events
└── …其余服务同理
```

**四条规则**：

1. **契约隔离**：`*-client` 只依赖 `pay-contracts-*` 与 `commons`，禁止依赖其它服务的 `*-client`，也禁止依赖本服务 `-domain`。谁消费某事件就依赖 `pay-contracts-events`，不经过生产方 client 转手。
2. **兼容性优先**：跨服务事件与公开 API 一律 **additive/compatible** 演进——新增可选字段允许；重命名/删除/改变类型或语义不允许；破坏性变更必须 `schemaVersion+1` 并显式迁移。把“快速开发、直接迁移调用方”限定在纯内部实现，**契约边界不做破坏性变更**。
3. **未知字段宽容、类型严格**：事件边界放开 `unknown=true`（明确 allowlist），但金额/时间/枚举/哈希仍严格（现有 JSON 契约测试保留）。这样生产方先加字段，消费方无需任何改动即可滚动共存。
4. **契约即工件**：`pay-contracts-events` 独立 Maven 版本号；每个消费方锁定构建时的版本；滚动发布让新旧版本共存。加一个 wire-compat 测试（JSON golden file）在 CI 里拦截破坏性变更。

**验收**：`payment-client` 不再被 `realtime-client`/`acquire-client` 传递引入；给某个事件 DTO 加一个可选字段，能证明只重编 `pay-contracts-events` 与其直接消费方，不需要动其它服务逻辑。

---

### D3 · 身份：三平面分开，管理台收敛为 UPMS 单体

系统实际有三个身份平面，各自独立，不要共用一套：

| 平面 | 主体 | 凭证 | 签发方 | 校验方 | 现状 |
|---|---|---|---|---|---|
| 商户接入 | MERCHANT / MERCHANT_APP | client_id/secret → JWT | oauth2-server（client_credentials） | acquire 等资源端 | 只有 dev 脚本签 token，未落地 |
| 内部服务 | SERVICE | 见下“瘦身保留（已决）” | oauth2-server | 各资源端 | 现有 service-auth（5 应用） |
| 运营管理 | OPERATOR（用户） | 账号密码 → JWT + RBAC | UPMS `/auth/login` | 管理台 | 未部署 |

**商户接入**：保留 oauth2-server 作为商户/应用令牌签发方。把 `ServiceTokenCustomizer` 的 `principal_type` 从写死 `SERVICE` 改为按客户端注册类型区分（`MERCHANT_APP`/`MERCHANT`），`tenant_id`、`merchant_id`、`aud`、`token_use` 照旧。商户应用凭 client_id/secret 取 5 分钟令牌；资源端继续本地 JWKS 验签。商户入驻（持久化开通、商户 JWT）仍是后续业务切片，但身份机制先定型。

**内部服务鉴权（已决：瘦身保留）**：保留 oauth2-server 签发内部令牌，但砍掉重度机制：

- 删除 per-audience / per-permission 目录与 per-call 按 audience 取不同令牌：每个服务缓存**一个**短期内部令牌（`sub=service`，资源端只验签名 + issuer + exp）。
- 资源端保留“这是合法的 SERVICE 令牌”这一认证，不再对每个内部端点做细粒度权限矩阵校验。
- 改动集中在 `loadup-components-service-auth` 的配置、`service-authorizations.json`（收缩为单一内部 scope）与 5 个应用的 yml。

保留它的唯一理由是纵深防御：内部接口不裸奔，SSRF/越权不能零成本横向调用。既然“所有应用不对外暴露、内部端点不进网关”这一前提成立，后续若发现仍过重，可再评估彻底去掉（NetworkPolicy 单层），本次不采用。

**运营管理（你的方案，采纳）**：以 UPMS 为基础搭账号密码登录 + RBAC。做法是新建一个 **admin 单体应用**（见 Phase 2）：内嵌 `loadup-modules-upms`（web + authserver，提供 `/auth/login` 与 `/upms/**`）以及 audit / dictionary / file / transfer 模块，再把现在 merchantmng / productmng 的控制器挪进来。它同时是前端 `/api/upms/**`、`/api/account/security/**`、`/api/audit/**` 等接口的宿主。站内信/SMS 通知**不在** admin 内，而是归 notificationcenter（见下）。

**AuthServer 改名（已决：`oauth2-server`）**：它只做 OAuth2 `client_credentials`、签发机器/应用令牌，不承载用户登录（用户登录在 UPMS），名字直接表明协议身份。落地时同步改：目录与 artifactId（`authserver-*` → `oauth2-server-*`）、包名 `io.github.loadup.authserver` → `io.github.loadup.oauth2server`、`deploy/config/*/authserver/` → `deploy/config/*/oauth2-server/`、`service-authorizations.json`、各应用 `SERVICE_AUTH_TOKEN_URI`（内部令牌保留，指向新 token endpoint `/oauth2/token`）。

**验收**：操作员能用账号密码登录拿 JWT 并调用管理接口；商户应用能凭 client_id/secret 拿 MERCHANT_APP 令牌调 acquire；内部接口鉴权方式有 ADR 记录并全部落码。

---

### D4 · KMS 与 Secret Manager：KMS 用 OpenBao Transit，Secret 走 Secret Manager + ESO

KMS 组件已经有 `local` 和 `openbao` 两个 binder（[README](../../loadup-framework/loadup-components/security/loadup-components-kms/README.md)）。两个职责要分清：

- **KMS（应用层，管密钥操作）**：签名（商户签名、JWT RS256）与字段加密（payment input fingerprint、渠道凭证加密）。生产环境用 `binder=openbao`（OpenBao Transit），私钥/对称密钥留在远端；`local` 只用于本地开发。
- **Secret Manager（K8s 层，管运行时秘密分发）**：数据库密码、OAuth client secret、渠道密钥、`OPENBAO_TOKEN` 等。来源 = OpenBao KV（或云 Secret Manager），经 **External Secrets Operator** 同步成 K8s Secret，再接入现有 `<app>-secret-<SECRET_VERSION>` 版本化流程。

也就是说：**Secret Manager 不放进服务内部，也不替代 KMS**——它是 `<app>-secret` 的“上游数据源”；KMS 是应用里 `KmsTemplate` 调用的“业务密钥后端”。两者可以共用同一个 OpenBao，但分层不同。

**落地**：

1. 生产 `loadup.kms.binder=openbao` + AppRole/Kubernetes 登录的 `KmsTokenProvider`（README 已定义注入点）；dev 保持 `kms-local`。
2. 把 `deploy/config/*/*/secret.env` 的值改为占位符，真实值进 Secret Manager，用 ESO 生成 Secret；仓库只存引用名。
3. 清理：删除已提交的 `authserver.p12`，改为部署期生成/挂载（脚本已支持）；OTel Collector 镜像去掉 `latest` 固定版本；镜像加 SBOM + 签名 + 漏洞扫描（Trivy/cosign）。
4. 密钥轮换走 KMS `KmsKeyManager.rotate` + Secret 版本切换，记录回退窗口。

**验收**：一个服务在生产配置下用 OpenBao 完成一次签名与一次 AES 加解密；DB 密码/OAuth secret 从 Secret Manager 注入而非仓库文件；`.p12` 不再被 git 跟踪。

---

### D5 · 数据库迁移：Flyway 归集到应用，组件不再 ship 迁移脚本

现状 6 个组件在 JAR 里带 `db/migration`（database、dfs-binder-database、gotone-store-jdbc、delayedtask、globalunique、outbox），升级框架就可能静默改业务库、且版本号跨模块冲突。

**目标**：每个应用拥有**唯一一份有序迁移历史**，落在自己的 `src/main/resources/db/migration`；框架组件只提供 schema 模板，不提供 Flyway 脚本。

**落地**：

1. 框架把组件 DDL 从 `db/migration/` 移到 `src/main/resources/db/schema/<component>.sql`（非 Flyway 目录，Flyway 不会扫）。
2. 新增 `scripts/gen-migrations.py`：读取应用依赖清单，把所需的 `<component>.sql` 以 `V<timestamp>__<component>.sql` 落进该应用的 `db/migration`（时间戳排序稳定、只包含该应用真正用到的组件）。
3. 应用 `flyway.locations` 只指向本应用 `classpath:db/migration`，不再隐式扫描依赖 JAR。
4. CI 加一条 gate（复用 framework 的 `ArchitectureTest` 思路）：断言组件 JAR 不包含 `db/migration` 资源；`gen-migrations.py` 输出与仓库一致（幂等）。

**验收**：删除本地所有旧构建产物后，`mvnd clean` 下每个应用只执行自己 `db/migration` 里的脚本，无重复 Flyway 版本、无依赖 JAR 带来的脚本；`gen-migrations.py` 可重复运行且结果不变。

---

## 2. 目标架构

```text
        ┌────────────── Envoy Gateway (Gateway API，唯一对外入口) ──────────────┐
 商户/收银台 ─► /api/acquire /api/cashier /checkout /checkout.js                │
 运营人员   ─► /api/admin  ·  /api/notifications/**                             │
 渠道回调   ─► /api/payment/channel-callbacks …                                 │
        └───────┬──────────────────────────────────────────────┬───────────────┘
                │                                              │
       ┌────────▼──────────┐                        ┌──────────▼──────────┐
       │  admin (单体)      │                        │  acquire ──sync──► payment
       │ UPMS+审计+字典+     │                        │    │           ▲      │
       │ file+transfer      │                        │    └─outbox────┘      │
       │ +商户/产品管理 BFF │                        │         ▼             │
       └──┬─────────────┬───┘                        │ limitcenter ◄──► payment
          │HTTP         │HTTP                        │         ▼             │
   merchantcenter  productcenter                     │ realtime(投影)         │
          └──────────────┴───────────────────────────┤ reconciliation ─► payment
                                                    │ merchantcenter/productcenter
                                                    └────────────┬────────────┘
                                          notificationcenter ◄── 各服务 submit（站内信/SMS）
                                          oauth2-server：商户/应用令牌 + 可选内部令牌
```

- 所有服务**不直接对外**；服务间走集群内 ClusterIP DNS；`/api/internal/**` 与 Actuator 不进网关。
- notificationcenter 独立部署：站内信读接口经网关给操作员，`submit` 供服务间投递，SMS/第三方渠道后续接入。
- oauth2-server（原 authserver）：只发机器/应用令牌，不承载用户登录。

数据所有权、金额/状态机、Outbox/Inbox/租约/围栏等现有设计**一律不动**。

---

## 3. 分阶段实施步骤

### Phase 0 · 验证基线（安全网，先做）

**目标**：让后续所有改动有可重复证据。

1. 给 `loadup-pay` 建 CI（照搬 [framework ci.yml](../../loadup-framework/.github/workflows/ci.yml) 的变更检测 + 窄模块 `mvnd clean verify` + Testcontainers）。
2. 一条 `docker compose` 端到端冒烟：Postgres + AuthServer + 7 服务，跑 create → pay → refund → realtime → reconciliation 取数，结果归档到 `deploy/acceptance-results/`。
3. 把“未运行”的测试至少跑一遍，修正失败项。

**验收**：PR 能自动跑窄模块测试并出覆盖率；端到端冒烟脚本可一条命令复现。

---

### Phase 1 · 单租户化（D1）—— 小改动，先做

1. 改 `database` 组件与各服务 `application.yml`：`multi-tenant.enabled=false`、移除 `required=true`。
2. `DatabaseTenant` 固定解析默认 schema，非默认租户 fail-fast。
3. 写 ADR。

**验收**：全服务在单租户模式下启动、迁移、冒烟通过；非默认租户被拒。

---

### Phase 2 · 管理台收敛 + 身份闭合（D3）—— 最大块

1. 新建 `loadup-pay/admin/` 单体应用（bootstrap 聚合）：
   - 内嵌 UPMS（web + authserver，`/auth/login`、`/upms/**`）；
   - 内嵌 audit / dictionary / file / transfer 模块的 web 适配（**不含** notification，通知归 notificationcenter）；
   - 迁入 merchantmng、productmng 的控制器（BFF，无 DB）。
2. 删除 merchantmng、productmng 两个独立应用；`loadup-admin` 前端 `/api` 代理指向 admin。
3. authserver 改名 oauth2-server：`ServiceTokenCustomizer` 支持按客户端类型发 `MERCHANT_APP`/`MERCHANT` 令牌；商户应用客户端注册流程（先最小闭环：脚本/受控配置注册）。
4. 内部鉴权按 D3 决定（瘦身保留）落地：收缩为单一内部 scope + 每服务一个短期令牌，写 ADR。
5. notificationcenter **保留不动**，本阶段只确认它仍是独立投递服务；SMS/第三方渠道接入（Gotone 正式渠道、真实发送）列为后续业务切片。
6. 网关路由按 0.1 的映射补齐：`/api/admin` → admin、`/api/notifications/**` → notificationcenter；确认 `/api/internal/**` 与 Actuator 不在网关路由内。

**验收**：运营登录 + 商户/产品管理页可用；商户应用令牌可调 acquire；内部鉴权方式全量落码并有 ADR；notificationcenter 继续作为独立服务部署、可被其它服务 submit。

---

### Phase 3 · 契约解耦（D2）

1. 抽 `pay-contracts-events`，把 payment/acquire/… 的跨服务事件 DTO 移入，去掉 `domain` 与其它 client 依赖。
2. 拆断 client 链：`realtime-client`、`acquire-client` 只依赖 `pay-contracts-events`。
3. 引入兼容性规则 + wire-compat JSON golden 测试 + `schemaVersion` 演进策略（修复 `requireSource` 硬编码 `==1`）。
4. 独立版本号 + 滚动发布演练（先加一个可选字段，证明无需动消费方）。

**验收**：依赖图上无 client 链式传递；一次 additive 事件变更无需改动任何消费方即可滚动上线。

---

### Phase 4 · 迁移归集（D5）

按 D5 四步执行；与 Phase 1 可并行。

**验收**：`gen-migrations.py` 幂等；组件 JAR 无 `db/migration`；每应用单一迁移历史。

---

### Phase 5 · KMS/Secret + 供应链 + 观测（D4）

1. OpenBao 接入：`kms-binder-openbao` + `KmsTokenProvider`；dev 保持 local。
2. ESO 同步 Secret；`secret.env` 改占位。
3. 清理 `.p12`；OTel 镜像定版；SBOM/签名/扫描。
4. 观测补 Tempo/Loki + 三条告警（outbox 最老待处理年龄、5xx 比例、连接池饱和）。

**验收**：生产配置下 KMS 走 OpenBao；Secret 不进仓库；观测闭环能看到一次跨服务 trace。

---

## 4. 已决事项

- 内部服务鉴权：**瘦身保留**。
- 管理台位置：**`loadup-pay/admin`** 单体。
- AuthServer 改名：**`oauth2-server`**。
- 契约模块：**`pay-contracts-events` 放 `loadup-pay`**。
- 商户入驻：**暂不排期**。
- SMS/第三方渠道：**暂不排期**。

### 4.1 实施进度

| 项 | 状态 | 说明 |
|---|---|---|
| D1 单租户化 | ✅ 完成 | database 组件 fail-fast + 7 个服务配置改为 `enabled=false` + ADR 0001；framework 编译通过 |
| D3a oauth2-server | ✅ 完成 | 目录/包/artifactId/类改名；商户 `MERCHANT_APP`/`MERCHANT` 令牌；`/auth/login` 增加 `principal_type=OPERATOR`；内部服务令牌改为单 scope `internal`（catalog、app 配置、校验规则同步） |
| D3b admin | ✅ 骨架完成 | 新建 `loadup-pay/admin`，内嵌 UPMS（web+authserver+SAS+KMS）+ merchant/product 管理控制器；删除 merchantmng/productmng；audit/dictionary/file/transfer 工作台模块待接入 |
| D3c 部署层 | 🟡 部分完成 | scripts/config 改名（oauth2-server/admin）、common.env issuer/token URI、pay.yml Deployment/Service/NetworkPolicy 已更新；真实渲染/部署未验收 |
| D2 contracts | ✅ 完成 | `pay-contracts-events` 收纳事件、状态枚举、付款动作/交互结果、payment command/query/DTO、product 条款 DTO 树；`acquire-client` 不再依赖 `payment-client`，`payment-client` 不再依赖 `productcenter-client`；仅 `acquire-infrastructure` 保留 `payment-client` 作为 HTTP 传输适配器依赖 |
| D5 migrations | ✅ 完成 | 6 个组件 DDL 从 `db/migration` 移到 `db/schema`；新增 `scripts/gen-migrations.py`（幂等 + `--check`），已生成 acquire/payment/reconciliation/merchantcenter/productcenter/notificationcenter 的迁移文件 |
| 编译 | ✅ 全量通过 | `mvnd clean test-compile -DskipTests`（pay 52 模块）BUILD SUCCESS；framework 相关模块 BUILD SUCCESS |
| 测试 | ✅ 全绿 | 全仓 `mvnd test -fae -DskipITs` BUILD SUCCESS；framework `loadup-commons-json` 39/39；覆盖单租户语义、Mockito 重打桩、冻结 digest、微秒时间序列化与持久化拒绝语义 |

---

## 5. 风险与回滚

- **Phase 2 是单体化回摆**：合并 mng 不是“违背微服务”，而是把“无数据库的纯 BFF”从独立进程改回模块，符合“默认模块化单体”原则；center 数据所有权不变，随时可再拆。
- **契约迁移是一次性批量改动**：涉及多个 client 模块的 import 调整，用 CI + 契约测试兜底，分批提交。
- **迁移归集会重写历史**：开发库可重建（项目处于快速开发阶段，符合既有约定）；已有环境用一次性手写迁移合并，不做 Flyway repair。
- **内部鉴权瘦身保留**：本次不彻底去掉 service-auth，避免内部接口裸奔；后续若仍觉过重再评估彻底去掉（届时回退需重新引入令牌链路）。
