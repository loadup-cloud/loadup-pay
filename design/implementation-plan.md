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

### D2 · 契约归提供方，消费方持有本地模型

**结论**：本轮直接全量重构，不保留兼容包名、转发类或历史格式适配。继续使用同一 reactor 发布版本。

1. Payment 的命令、查询、响应和错误码归 `payment-client`；Product Center 的条款和准入契约归 `productcenter-client`。
   Payment 接受请求使用本服务的冻结条款表示，不能通过 DTO 字段传递依赖 Product Center client。
2. 所有 client 移除 domain 和其他 Pay 服务 client 依赖；公开参数、恢复及执行枚举归本服务 `client.enums`，domain 可以依赖这些纯类型。
3. 消费方公开表示放在本服务 client，应用端口使用本地 `app.model.<provider>` 模型。其他 Pay 服务 client 仅由 infrastructure 依赖，
   HTTP 适配器通过 Spring 管理的 MapStruct `RemoteContractConverter` 转换请求、响应、分页及 sealed 子类型。
4. `pay-contracts-events` 收缩为按发布方分包的七个集成事件和三个公开事件状态枚举；不包含付款请求响应、产品条款或渠道 API 依赖。
5. 一次性更新生产源码、测试及 Maven 依赖。CI 结构门禁检查 client/app/domain 依赖方向、事件工件职责及 reactor 无环。

**验收**：没有旧 `contracts.payment/product/model` 包或兼容别名；client 无 domain/其他服务 client 依赖；
消费方 app/domain 不导入远端 client；HTTP 请求响应通过适配层转换，金额、状态、时间、摘要及错误处理不变。
静态检查与编译、测试、真实调用分别记录，不把已更新源码视为运行验收通过。
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

### D5 · 数据库迁移：应用选择迁移命名空间，历史只追加

组件 schema 已移到非 Flyway 的 `db/schema`。框架业务模块仍保留正式自动迁移；
`classpath:db/migration` 会扫描依赖 JAR 中同名位置，不能用它表达“只扫描应用自身”。

**目标**：每个应用使用一个 Flyway 实例和一份有序迁移历史，显式选择应用自己的目录及获准的框架业务模块目录。
组件初始快照、Framework 完整初始化文件、后续增量升级分别管理。

**落地**：

1. 组件继续提供 `db/schema/<component>.sql`；框架业务迁移使用 `db/migration/framework/<module>/V*.sql`，保留自动执行能力。
2. Pay 自有迁移及组件快照放在 `db/migration/pay/<app>/`。应用通过 `loadup.flyway.locations` 显式选择这些目录：
   admin 额外选择 `framework/upms`；notificationcenter 选择 `framework/notification`；reconciliation 选择 `framework/file`、`framework/transfer`。
   oauth2-server 保留已有独立的 `db/migration/oauth2-server`，由 Spring Boot 管理。禁止配置共享根 `classpath:db/migration`。
3. `scripts/gen-migrations.py` 仅创建尚未登记的初始组件快照，并以 `scripts/component-migrations.lock.json` 冻结已有快照哈希。
   修改模板不会覆盖旧版本，移除组件依赖不会删除历史，已登记文件修改或丢失会报错；`--check` 不创建目录、不写文件，也不依赖最新 Framework checkout。
4. 组件结构升级编写新的 `V<timestamp>__<change>.sql`（包括必要的 ALTER），版本高于当前应用历史；不重新生成或改写已应用版本。
   新增组件的首次快照须审核版本顺序，已有环境需要升级时直接追加新版本，不把初始模板当作升级脚本。
5. CI 检查应用 locations 白名单、框架模块目录隔离和冻结快照校验。完整 `framework-schema.sql` 仅用于空 schema 一次性手动初始化，
   不登记 Flyway 历史；手动导入与自动迁移的基线由部署方明确选择，不自动 repair/baseline。

**验收**：新构建的应用只发现自己的命名空间及显式允许的框架模块，无重复版本；已有迁移移动目录后文件名、版本及内容保持不变。
修改模板或移除依赖不会改写历史，冻结快照变动会被检查拦截。实际 Flyway 空库初始化和已有库升级需分别运行验收，静态检查不代表已通过数据库验证。

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

### Phase 3 · 契约所有权重构（D2）

1. 将公共包里的付款、产品请求响应归还提供方 client，公共事件工件只保留事件和事件状态。
2. 将对外使用的 domain 枚举移到本服务 client，清除 client 对 domain 的反向依赖。
3. 消费方使用本地模型，由基础设施 MapStruct Converter 转换；移除 app/domain 对其他 Pay 服务 client 的依赖。
4. 更新全部调用方和测试，不提供兼容层，不引入独立契约版本发布或历史消息格式分支。

**验收**：结构门禁通过，模块无依赖环，远端类型只出现在适配层；编译、测试和真实运行按独立证据验收。

---

### Phase 4 · 迁移归集（D5）

按 D5 四步执行；与 Phase 1 可并行。

**验收**：`gen-migrations.py --check` 验证冻结历史；组件不携带 Flyway 迁移，业务模块保留独立命名空间的自动迁移；每应用显式选择 locations 并使用单一历史。

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
| D2 contracts | 🟡 全量重构源码，运行待验收 | 提供方拥有请求响应，消费方使用本地模型及基础设施 MapStruct 转换；公开枚举归 client；公共事件包只保留事件及事件状态；无兼容层。构建与测试尚未执行 |
| D5 migrations | 🟡 源码修正，运行待验收 | 应用与框架业务模块按命名空间隔离，自动迁移保留；16 份旧组件快照保持内容，admin 补 Sequence 初始快照；生成器只创建缺失快照，锁文件校验历史，不覆盖或删除；静态检查与实际数据库验收分开记录 |
| 编译 | ✅ 全量通过 | `mvnd clean test-compile -DskipTests`（pay 52 模块）BUILD SUCCESS；framework 相关模块 BUILD SUCCESS |
| 测试 | ✅ 全绿 | 全仓 `mvnd test -fae -DskipITs` BUILD SUCCESS；framework `loadup-commons-json` 39/39；覆盖单租户语义、Mockito 重打桩、冻结 digest、微秒时间序列化与持久化拒绝语义 |

---

## 5. 风险与回滚

- **Phase 2 是单体化回摆**：合并 mng 不是“违背微服务”，而是把“无数据库的纯 BFF”从独立进程改回模块，符合“默认模块化单体”原则；center 数据所有权不变，随时可再拆。
- **契约迁移是一次性批量改动**：涉及多个 client 模块的 import 调整，用 CI + 契约测试兜底，分批提交。
- **迁移目录调整**：只移动资源位置，保留 SQL 文件名、版本和内容；发布前必须 clean，避免旧路径残留。已有数据库不自动 clean、repair 或 baseline。
  先发布或安装包含新目录的 Framework 制品，再构建 Pay，避免应用配置与依赖资源版本不匹配。
  保留数据的环境一律追加升级脚本；回退应用需使用匹配的迁移资源，不能删除已应用历史。
- **内部鉴权瘦身保留**：本次不彻底去掉 service-auth，避免内部接口裸奔；后续若仍觉过重再评估彻底去掉（届时回退需重新引入令牌链路）。
