> 2026-11-04 应用拆分：customer → merchantcenter，product → productcenter；新增 merchantmng/productmng，后续加入 notificationcenter。以 [总体架构](../ARCHITECTURE.md) 的当前应用边界为准。两个后管无数据库，center 仅内部访问。

# 构建与部署脚本

用于十一个应用（十个业务应用和 OAuth server）的本地JAR、Docker镜像和OrbStack Kubernetes部署。所有脚本统一使用 Python 入口；应用目录不再提供 shell wrapper，使用 `python3 scripts/build.py <应用名>`、`python3 scripts/deploy.py <应用名>` 等命令。

| 入口                                | 行为                                            |
|-------------------------------------|-------------------------------------------------|
| `python3 scripts/build.py all`            | 一次Maven构建十一个应用及其依赖，然后构建十一个镜像 |
| `python3 scripts/deploy.py all`           | 构建JAR/镜像、部署十一个应用、等待就绪端点        |
| `python3 scripts/build.py payment`        | 仅构建payment及其必需依赖，生成payment镜像        |
| `python3 scripts/deploy.py payment`       | 构建并部署payment，不修改其他应用                 |
| `python3 scripts/verify.py [all或应用名]` | 检查选定Deployment与Service ready EndpointSlice |
| `python3 scripts/render.py [all或应用名]` | 仅渲染镜像/配置版本引用清单供审查，不 apply |
| `python3 scripts/up.py [all或应用名]` | 校验并创建配置快照，再调用 deploy.py 构建及部署 |
| `python3 scripts/configmap.py <动作> [all或应用名]` | 初始化、检查、创建、换版、查看和删除配置 |
| `python3 scripts/dev_merchant_token.py --merchant-id <id>` | 仅 dev：用 OAuth server 本地 KeyStore 签发测试商户 JWT 到 stdout |

## 环境配置与一键部署

数据库准备可使用下文的 [Framework 初始化 SQL 手动导入](#framework-初始化-sql-手动导入)，并按说明协调应用迁移。

直接维护 `deploy/config/dev|test|prod` 的 common.env、应用 config.env/secret.env。OAuth server 另有忽略提交的 oauth2-server.p12，OAuth 客户端记录由数据库管理且需预先创建。配置含义见 [环境目录](../deploy/config/README.md) 与 OAuth server 的数据库客户端管理说明。

```bash
# 先准备独立 OAuth server 数据库 数据库。构建脚本默认全量安装相邻 framework 的组件。
# dev 的 up.py oauth2-server/all 会自动准备缺失的 RSA KeyStore 和应用凭证。
APP_ENV=dev python3 scripts/configmap.py check all
APP_ENV=dev IMAGE_TAG=oauth-001 python3 scripts/up.py oauth2-server
APP_ENV=dev IMAGE_TAG=auth-002 python3 scripts/up.py all
# 仅创建快照，暂不发布：
APP_ENV=dev python3 scripts/configmap.py update payment
# 非 dev 必须指定 context，配置目录不会自动切换集群。
APP_ENV=test KUBE_CONTEXT=my-test-cluster python3 scripts/configmap.py create all
# 查看或删除明确版本；删除范围为 config（默认）、secret、all。
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused python3 scripts/configmap.py status payment
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused python3 scripts/configmap.py delete payment config
```

up.py 按选定应用的内容计算 cfg-<环境>-<摘要> / sec-<环境>-<摘要>，创建不可变快照并发布。普通配置进入 ConfigMap 摘要，密码及 PKCS12 进入 Secret 摘要；相同对象复用，同名不同内容拒绝。CONFIG_DIR 可覆盖完整环境目录；单应用与 all 摘要范围不同，回退使用归档中的实际版本。

业务应用自动获取和缓存短期 OAuth2 服务令牌，不再挂载 JWT 文件，也不需要每次部署签发令牌。私钥仅挂载 OAuth server，公钥经 JWKS 发布。旧离线签发脚本和 tokens 子命令已移除；旧 ignored 文件及运行资源没有自动删除。

## Framework 初始化 SQL 手动导入

Framework 保留组件原始 `db/schema` 模板和业务模块 Flyway 迁移，并提供生成的完整 `sql/framework-schema.sql`。
整合版包含六份组件 SQL 和七份业务模块迁移，先组件、再按版本执行业务迁移。在 Pay 根目录执行以下命令，
先更新 Framework 整合版，再手动引入 Pay 并导入明确指定数据库的空 schema：

```bash
python3 ../loadup-framework/tools/export-schema.py
mkdir -p scripts/sql
cp ../loadup-framework/sql/framework-schema.sql scripts/sql/framework-schema.sql
PGHOST=localhost PGPORT=5432 PGUSER=admin \
  psql -X --dbname=loadup_payment --set=ON_ERROR_STOP=1 --single-transaction \
  --file=scripts/sql/framework-schema.sql
```

密码通过 `.pgpass` 或交互输入提供。也可直接在数据库工具中导入 Framework 的整合文件，无需复制。
使用制品而没有 Framework 源码时，可取得与制品版本匹配的 `sql/framework-schema.sql` 后执行同一条 psql 命令。
该文件没有 psql 专有命令，可用于 PostgreSQL 数据库工具的 SQL 编辑器。

这是手动操作入口，build/deploy/up 不会执行它。每次只导入指定的数据库，不会遍历 Pay 的数据库。
合并 SQL 使用连接的 `search_path`，自定义 schema 时需事先创建并选择与应用相同的 schema。
整合版会创建 Framework 全部组件和业务模块的表，Pay 自己的业务表仍需应用迁移。
它不登记 Flyway 历史，部分业务表和索引没有 `IF NOT EXISTS`，因此仅能用于空 schema 的一次性初始化，不能重复导入。
手动管理迁移的环境可配置 `loadup.flyway.migrate-at-start=false`，并自行执行 Pay 应用所需的其他迁移。
若后续使用自动迁移，需先按实际导入范围建立匹配的 Flyway 基线，不能直接从头重跑同一批迁移。

现有自动方式继续保留：应用显式扫描 `classpath:db/migration/pay/<app>`，并选择获准的 Framework 模块命名空间。
admin 选择 `framework/upms`，notificationcenter 选择 `framework/notification`，reconciliation 选择 `framework/file` 和 `framework/transfer`。
oauth2-server 已有独立目录 `db/migration/oauth2-server`。`classpath:db/migration` 不是应用隔离范围，不应配置为扫描根。

`python3 scripts/gen-migrations.py` 只创建缺失的组件初始快照，提交时同时包含 SQL 和 `scripts/component-migrations.lock.json`。
`python3 scripts/gen-migrations.py --check` 只检查已登记快照的存在及哈希，不依赖 Framework checkout，也不写目录或文件。
模板变化不会覆盖快照，移除组件依赖不会删除历史；已登记快照修改或丢失会报错，须恢复原文件。
组件结构升级要编写新的、经过审核的 `V<timestamp>__<change>.sql`，版本高于应用当前历史；新增组件也需审核首次执行顺序。
新版本可能需要 ALTER，不能用重新复制当前 CREATE 模板代替升级。手动整合版也不能替代增量迁移。

本次目录调整保留了已有 SQL 的文件名、版本与字节内容。重新构建时须使用 clean 清除旧路径资源；不对数据库自动执行 clean、repair 或 baseline。
先构建或发布包含新迁移目录的 Framework 制品，再构建 Pay；旧 Framework JAR 不包含新的模块命名空间，不能与本次 locations 配置混用。
完整说明见 [Framework SQL](../../loadup-framework/sql/README.md)。

## 开发用商户 JWT

OAuth server 只签发 SERVICE 令牌，不提供用户/商户登录。本地测试商户 token 由 dev KeyStore 签发，脚本仅允许 `APP_ENV=dev`：

```bash
TOKEN=$(python3 scripts/dev_merchant_token.py --merchant-id 6f1c2a3e-9d4b-4f7a-8c2e-1b5d7a9c0e11)

# 覆盖 aud、permissions、principal_type 或有效期
TOKEN=$(python3 scripts/dev_merchant_token.py --merchant-id <id> --subject <app-id> \
  --principal-type MERCHANT_APP --audience acquire,productcenter,merchantcenter \
  --permissions pay:acquire:create,pay:product:admit --ttl-seconds 1800)
```

默认从 `deploy/config/dev/oauth2-server/oauth2-server.p12` 和 `secret.env` 的 `KMS_KEYSTORE_PASSWORD` 读取签名材料；私钥只在临时目录短暂存在，token 输出到 stdout。`iss` 默认取 `CONFIG_DIR/common.env` 的 `JWT_ISSUER_URI`，`kid` 默认 `<AUTHSERVER_SIGNING_KEY>-v1`，可用 `--issuer`/`--kid` 覆盖。不要提交 token 或私钥；test/prod 必须使用真实 IdP 签发的令牌。

外部调用走 Gateway API，不要 `port-forward` 或直连内部 Service。当前本地网关 `pay.loadup.local` 使用自签名证书：

```bash
TOKEN=$(python3 scripts/dev_merchant_token.py --merchant-id <merchant-id>)

curl --noproxy '*' --resolve pay.loadup.local:443:192.168.139.2 \
  --cacert deploy/gateway/rendered/pay.loadup.local.crt \
  -X POST https://pay.loadup.local/api/acquire/orders/create \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"payeeMerchantId":"<merchant-id>","merchantOrderNo":"TEST-ORDER-004",
       "productScopeKey":"default","amountMinor":100,"currency":"CNY","subject":"Test order"}'
```

网关按 `/api/acquire` 前缀转发到 acquire，并负责 TLS、限流和超时；JWT 仍由后端资源服务器校验。`--resolve` 的 IP 以本地网关实际地址为准。

管理操作同样走网关：catalog / limit publication 使用 `/api/productmng`，合约 preview/sign 使用 `/api/merchantmng/product/merchant-contracts/*`；`productcenter` 和 `merchantcenter` 不直接对外。当前集群若仍保留旧的 `/api/product`、`/api/customer` 路由，需要先按 [网关说明](../deploy/gateway/README.md) 用 `values-local.yaml` 升级 Gateway chart。

版本变化通过滚动重启生效，不承诺 ConfigMap 自动刷新 Bean。test/prod 默认要求 HTTPS token endpoint。凭证与签名密钥轮换、回退限制见 [发布约定](../deploy/CONFIGURATION.md)。

应用清单：oauth2-server、merchantcenter、merchantmng、productcenter、productmng、limitcenter、payment、acquire、realtime、reconciliation、notificationcenter。up.py 管理应用配置及发布，不创建数据库、RustFS 或网关控制器；OAuth server 本身已纳入应用清单。dev 的 all/oauth2-server 发布会补齐缺失的本地签名凭证，已有非空值与密钥保留。当前部署仍固定 loadup-pay namespace，使用 OrbStack 本地镜像，不自动发布远程镜像。

## 前提与默认行为

- Java 25：优先使用项目`mise.toml`已安装的运行时；无mise时使用`JAVA_HOME`/PATH并校验主版本。不自动下载Java，也不回退到Java
  21。
- 应用构建前默认对相邻 framework 执行全量 `clean install`，避免遗漏任一组件 jar；可用 `FRAMEWORK_DIR` 指定仓库。已安装匹配制品时显式设置 `SKIP_FRAMEWORK_BUILD=1`。独立入口为 `scripts/build_framework.py`；仅需最小集时可设置 `FRAMEWORK_SCOPE=targeted`。
- 使用`mvnd clean package -pl <选定bootstrap列表> -am -Dmaven.test.skip=true`
  ；全量应用只执行一次Maven，不逐应用重复清理共享依赖。默认不编译或执行测试，构建通过不等于单测通过。
- Docker和Kubernetes默认context均为`orbstack`，脚本通过显式context调用，不更改全局当前context。部署固定namespace为
  `loadup-pay`。
- 使用本地共享镜像及`imagePullPolicy: Never`；不会推送镜像。其他集群需另外实现镜像发布并修改拉取策略。
-

默认镜像标签为系统本地时间戳与进程ID，每次发布生成新标签。构建拒绝覆盖已有同名镜像，避免同标签重建后Pod仍使用旧镜像；复用已有镜像请使用SKIP_BUILD。源码清单不写入动态标签；部署时在内存中选择资源并绑定镜像。

- Dockerfile通过`JAR_FILE`接收实际构建产物，不固定项目版本；`.dockerignore`仅允许启动JAR与Dockerfile进入构建上下文。

## 指定版本与复用镜像

```bash
# 一次完成所有构建与发布
IMAGE_TAG=release CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 python3 scripts/deploy.py all

# 分开构建、部署，第二步复用同一批本地镜像
IMAGE_TAG=release-002 python3 scripts/build.py all
SKIP_BUILD=1 IMAGE_TAG=release-002 CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 python3 scripts/deploy.py all

# 单应用发布
CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 python3 scripts/deploy.py acquire

# 显式要求编译并执行所选应用及依赖的测试
python3 scripts/build.py payment -Dmaven.test.skip=false
```

部署显式要求 CONFIG_VERSION 与 SECRET_VERSION（不可变快照版本），RELEASE_DIR 可覆盖引用清单归档位置。
这两个版本参数不会自动创建配置；资源缺失时部署在构建前停止，并提示 [配置准备流程](../deploy/CONFIGURATION.md#发布)。
配置源直接维护在 [dev/test/prod 环境目录](../deploy/config/README.md)，可用 up.py 一次完成配置准备和发布。
支持环境变量`IMAGE_TAG`、`IMAGE_PREFIX`（默认`loadup-pay`）、`DOCKER_CONTEXT`与`KUBE_CONTEXT`。两端必须使用能访问同一批本地镜像的环境；
`SKIP_BUILD=1`必须指定已构建的IMAGE_TAG，脚本在应用资源前检查选定镜像存在。

`render_deploy.py`只处理`deploy/pay.yml`
经 kubectl 本地 dry-run 输出的 JSON：全量选择 34 个资源（Namespace、十一组 NetworkPolicy/Deployment/HTTP Service），单应用选择 4 个资源。
渲染器要求十一个应用均为普通 ClusterIP HTTP 8080，只允许 http 容器端口，拒绝旧 Headless Service、缺失或错配 Service。
全量发布先应用 OAuth server 并等待就绪，再逐个发布十个业务应用；保留完整发布归档，OAuth server 失败时不会继续启动消费者。单应用发布不会重新应用其他应用的镜像或副本配置。全部资源校验完成后才输出，管道启用pipefail；构建或镜像缺失时不提交部署。

## 验收范围

部署脚本等待每个选定Deployment
rollout与ready端点。Kubernetes中的startup/liveness/readiness调用Actuator基础健康入口。脚本不创建业务订单或发起真实业务请求，也不部署Headlamp；Headlamp仍由独立
`headlamp.py`管理。

已部署版本、实际检查结果与未执行项目见[实施说明](../ARCHITECTURE.md)。

## 配置与 HTTP 发布边界

十一个应用均使用标准 HTTP Service Clients，内部路径 /api/internal/v1/，目标按应用名约定 http://<app>:8080，仅特殊地址单独配置。
非 Kubernetes 环境使用同一制品，覆盖服务基础地址即可；无需 nacos profile 或 DUBBO_REGISTRY_ADDRESS。
当前开发阶段没有协议双栈或自动回退，十一个应用须使用匹配镜像、地址、JWT 与配置；旧协议回退也须恢复整套匹配配置和 Service。

部署前准备版本化 <app>-config-<CONFIG_VERSION> ConfigMap 与 <app>-secret-<SECRET_VERSION> Secret，二者必须 immutable。
仅 OAuth server 挂载版本化 oauth2-server-files-<SECRET_VERSION> KeyStore Secret，业务应用不再挂载令牌文件。
共享默认绑定前缀为 loadup.http.client，模块显式配置优先，旧 loadup.rpc.client 不再读取。
渲染器将配置版本写入 Pod template 并绑定具体资源名，配置换版触发滚动。环境变量/Bean 默认重启生效，OAuth 客户端按有效期自动续取令牌。
发布前只预检资源存在性及 immutable 元数据，归档可回退的镜像/引用组合；配置值与业务权限仍需应用和环境验证。
完整创建、轮换、回退、排空与未执行验证见 [版本化配置与发布](../deploy/CONFIGURATION.md)。

源清单不再包含 Triple 端口、Headless Service 或旧 RPC NetworkPolicy，改为八个每应用 HTTP 入站策略。apply 不会删除旧集群资源，发布方需确认旧调用退出后明确清理。
HTTP NetworkPolicy 和 Envoy Gateway 配置已补，运行仍待验收。网络策略随核心清单，网关 chart 独立由平台发布者安装，二者不自动安装控制器。verify.py 仅检查 Deployment/HTTP ready EndpointSlice，
不证明完整 JWT、业务幂等、PostgreSQL 事务、多副本连接分布、滚动排空或回退通过。

原生成类可能残留于增量输出，按约定 clean 构建。测试源码与未执行验收见 [迁移清单](../HTTP_MIGRATION.md)。
不把本说明视为部署或发送真实金融请求的授权。

## 托管收银台浏览器核心测试源码

H5 页面核心使用原生 ES module/BigInt，无新增前端依赖。用户恢复验证后，可在 loadup-pay 根目录单独执行：

```bash
node --test acquire/acquire-adapter/src/test/javascript/checkout-core.test.mjs
```

本轮没有执行此命令。该测试只覆盖纯逻辑，不替代 Java 生成代码/事务、浏览器/CSP/标签页存储、可信代理、渠道域名及真实 H5
跳转验收。静态页面与 /checkout.js、/checkout-core.mjs、/checkout.css 通过独立 GET 安全链公开；原金融 POST 仍使用逐次校验的会话能力。

## Reconciliation 业务镜像

`python3 scripts/build.py reconciliation` 构建独立对账服务。
配置独立 RECONCILIATION_JDBC_*、RECONCILIATION_JWT_* 和受控账户目录，详见 [对账服务](../reconciliation/README.md)。
十一个应用清单、镜像允许路径和 HTTP 选择器已同步为源码；本轮未构建镜像或运行渲染、部署、就绪脚本。
当前批次/查询/核对源码不包含已完成的实际账单下载与权威数据导入；不能将 Pod 就绪当成对账业务验收。

Reconciliation 手工文件导入已接 File/Transfer/S3 的业务装配源码；除 JDBC/JWT/账户目录，还需私有 RECONCILIATION_S3_BUCKET，
可选 REGION/ENDPOINT/PATH_STYLE 和一对 S3_ACCESS_KEY/S3_SECRET_KEY（或默认凭证链）。不自动创建 bucket；statement-account
显式配置实际主/子商户。
部署所需 Secret 内容及存储策略需由部署环境提供，本轮未发布或访问存储。当前未接官方账单自动下载/验签来源或 payment 权威导出。

网关和平台安装见 [Gateway API / Envoy Gateway](../deploy/gateway/README.md)，选择器与 CNI 验收见 [HTTP 网络策略](../deploy/NETWORK_POLICY.md)。
部署前使用 check_kubernetes.py 检查客户端/服务端版本差距；deploy.py 也执行此检查，不自动安装或升级 kubectl。

发布后的线程 A/B、池等待和业务验收见 [最终验收](../deploy/ACCEPTANCE.md)，使用记录模板保存原始证据，不把脚本 PASS 当作金融链路通过。
