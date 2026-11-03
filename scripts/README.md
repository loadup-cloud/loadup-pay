# 构建与部署脚本

用于八个应用（七个业务应用和 AuthServer）的本地JAR、Docker镜像和OrbStack Kubernetes部署。每个应用目录提供`build.sh`与`deploy.sh`，入口委托本目录的公共实现。

| 入口                                | 行为                                            |
|-------------------------------------|-------------------------------------------------|
| `./scripts/build.sh all`            | 一次Maven构建八应用及其依赖，然后构建八个镜像   |
| `./scripts/deploy.sh all`           | 构建JAR/镜像、部署八应用、等待就绪端点          |
| `./payment/build.sh`                | 仅构建payment及其必需依赖，生成payment镜像      |
| `./payment/deploy.sh`               | 构建并部署payment，不修改其他应用           |
| `./scripts/verify.sh [all或应用名]` | 检查选定Deployment与Service ready EndpointSlice |
| `./scripts/render.sh [all或应用名]` | 仅渲染镜像/配置版本引用清单供审查，不 apply |
| `./scripts/up.sh [all或应用名]` | 校验并创建配置快照，再调用 deploy.sh 构建及部署 |
| `./scripts/configmap.sh <动作> [all或应用名]` | 初始化、检查、创建、换版、查看、删除配置及管理授权配置 |

## 环境配置与一键部署

直接维护 `deploy/config/dev|test|prod` 的 common.env、应用 config.env/secret.env 与 service-authorizations.json。AuthServer 另有忽略提交的 authserver.p12。配置含义见 [环境目录](../deploy/config/README.md) 与 [AuthServer](../authserver/README.md)。

```bash
# 本地准备缺失的 RSA KeyStore 和匹配的应用凭证，不访问集群。
./scripts/authserver.sh prepare-dev
# 先准备独立 loadup_authserver 数据库。构建脚本会安装所需 framework 组件。
APP_ENV=dev ./scripts/configmap.sh check all
APP_ENV=dev IMAGE_TAG=auth-001 ./scripts/up.sh authserver
APP_ENV=dev IMAGE_TAG=auth-002 ./scripts/up.sh all
# 仅创建快照，暂不发布：
APP_ENV=dev ./scripts/configmap.sh update payment
# 非 dev 必须指定 context，配置目录不会自动切换集群。
APP_ENV=test KUBE_CONTEXT=my-test-cluster ./scripts/configmap.sh create all
# 查看或删除明确版本；删除范围为 config（默认）、secret、all。
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused ./scripts/configmap.sh status payment
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused ./scripts/configmap.sh delete payment config
```

up.sh 按选定应用的内容计算 cfg-<环境>-<摘要> / sec-<环境>-<摘要>，创建不可变快照并发布。授权 JSON 进入 ConfigMap 摘要，密码及 PKCS12 进入 Secret 摘要；相同对象复用，同名不同内容拒绝。CONFIG_DIR 可覆盖完整环境目录；单应用与 all 摘要范围不同，回退使用归档中的实际版本。

业务应用自动获取和缓存短期 OAuth2 服务令牌，不再挂载 JWT 文件，也不需要每次部署签发令牌。私钥仅挂载 AuthServer，公钥经 JWKS 发布。旧离线签发脚本和 tokens 子命令已移除；旧 ignored 文件及运行资源没有自动删除。

版本变化通过滚动重启生效，不承诺 ConfigMap 自动刷新 Bean。test/prod 默认要求 HTTPS token endpoint。凭证与签名密钥轮换、回退限制见 [发布约定](../deploy/CONFIGURATION.md)。

可选应用：authserver、customer、product、limitcenter、payment、acquire、realtime、reconciliation。up.sh 管理应用配置及发布，不创建数据库、RustFS 或网关控制器；AuthServer 本身已纳入应用清单。dev 的 all/authserver 发布会补齐缺失的本地签名凭证，已有非空值与密钥保留。当前部署仍固定 loadup-pay namespace，使用 OrbStack 本地镜像，不自动发布远程镜像。

## 前提与默认行为

- Java 25：优先使用项目`mise.toml`已安装的运行时；无mise时使用`JAVA_HOME`/PATH并校验主版本。不自动下载Java，也不回退到Java
  21。
- 应用构建前自动对相邻 framework 的 HTTP、resource-server、SAS、kms-local 及必需依赖执行窄范围 `clean install`；可用 `FRAMEWORK_DIR` 指定仓库。已安装匹配制品时显式设置 `SKIP_FRAMEWORK_BUILD=1`。独立入口为 `scripts/build-framework.sh`。
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
IMAGE_TAG=release CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 ./scripts/deploy.sh all

# 分开构建、部署，第二步复用同一批本地镜像
IMAGE_TAG=release-002 ./scripts/build.sh all
SKIP_BUILD=1 IMAGE_TAG=release-002 CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 ./scripts/deploy.sh all

# 单应用发布
CONFIG_VERSION=cfg-001 SECRET_VERSION=sec-001 ./acquire/deploy.sh

# 显式要求编译并执行所选应用及依赖的测试
./payment/build.sh -Dmaven.test.skip=false
```

部署显式要求 CONFIG_VERSION 与 SECRET_VERSION（不可变快照版本），RELEASE_DIR 可覆盖引用清单归档位置。
这两个版本参数不会自动创建配置；资源缺失时部署在构建前停止，并提示 [配置准备流程](../deploy/CONFIGURATION.md#发布)。
配置源直接维护在 [dev/test/prod 环境目录](../deploy/config/README.md)，可用 up.sh 一次完成配置准备和发布。
支持环境变量`IMAGE_TAG`、`IMAGE_PREFIX`（默认`loadup-pay`）、`DOCKER_CONTEXT`与`KUBE_CONTEXT`。两端必须使用能访问同一批本地镜像的环境；
`SKIP_BUILD=1`必须指定已构建的IMAGE_TAG，脚本在应用资源前检查选定镜像存在。

`render-deploy.py`只处理`deploy/pay.yml`
经 kubectl 本地 dry-run 输出的 JSON：全量选择 25 个资源（Namespace、八组 NetworkPolicy/Deployment/HTTP Service），单应用选择 4 个资源。
渲染器要求八应用均为普通 ClusterIP HTTP 8080，只允许 http 容器端口，拒绝旧 Headless Service、缺失或错配 Service。
全量发布先应用 AuthServer 并等待就绪，再逐个发布七个业务应用；保留完整发布归档，AuthServer 失败时不会继续启动消费者。单应用发布不会重新应用其他应用的镜像或副本配置。全部资源校验完成后才输出，管道启用pipefail；构建或镜像缺失时不提交部署。

## 验收范围

部署脚本等待每个选定Deployment
rollout与ready端点。Kubernetes中的startup/liveness/readiness调用Actuator基础健康入口。脚本不创建业务订单或发起真实业务请求，也不部署Headlamp；Headlamp仍由独立
`headlamp.sh`管理。

已部署版本、实际检查结果与未执行项目见[实施说明](../ARCHITECTURE.md)。

## 配置与 HTTP 发布边界

七应用均使用标准 HTTP Service Clients，内部路径 /api/internal/v1/，目标按提供方 BASE_URL 配置，默认 http://<app>:8080。
非 Kubernetes 环境使用同一制品，覆盖服务基础地址即可；无需 nacos profile 或 DUBBO_REGISTRY_ADDRESS。
当前开发阶段没有协议双栈或自动回退，七应用须使用匹配镜像、地址、JWT 与配置；旧协议回退也须恢复整套匹配配置和 Service。

部署前准备版本化 <app>-config-<CONFIG_VERSION> ConfigMap 与 <app>-secret-<SECRET_VERSION> Secret，二者必须 immutable。
仅 AuthServer 挂载版本化 authserver-files-<SECRET_VERSION> KeyStore Secret，业务应用不再挂载令牌文件。
共享默认绑定前缀为 loadup.http.client，模块显式配置优先，旧 loadup.rpc.client 不再读取。
渲染器将配置版本写入 Pod template 并绑定具体资源名，配置换版触发滚动。环境变量/Bean 默认重启生效，OAuth 客户端按有效期自动续取令牌。
发布前只预检资源存在性及 immutable 元数据，归档可回退的镜像/引用组合；配置值与业务权限仍需应用和环境验证。
完整创建、轮换、回退、排空与未执行验证见 [版本化配置与发布](../deploy/CONFIGURATION.md)。

源清单不再包含 Triple 端口、Headless Service 或旧 RPC NetworkPolicy，改为八个每应用 HTTP 入站策略。apply 不会删除旧集群资源，发布方需确认旧调用退出后明确清理。
HTTP NetworkPolicy 和 Envoy Gateway 配置已补，运行仍待验收。网络策略随核心清单，网关 chart 独立由平台发布者安装，二者不自动安装控制器。verify.sh 仅检查 Deployment/HTTP ready EndpointSlice，
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

`./reconciliation/build.sh` 构建独立对账服务。
配置独立 RECONCILIATION_JDBC_*、RECONCILIATION_JWT_* 和受控账户目录，详见 [对账服务](../reconciliation/README.md)。
七应用清单、镜像允许路径和 HTTP 选择器已同步为源码；本轮未构建镜像或运行渲染、部署、就绪脚本。
当前批次/查询/核对源码不包含已完成的实际账单下载与权威数据导入；不能将 Pod 就绪当成对账业务验收。

Reconciliation 手工文件导入已接 File/Transfer/S3 的业务装配源码；除 JDBC/JWT/账户目录，还需私有 RECONCILIATION_S3_BUCKET，
可选 REGION/ENDPOINT/PATH_STYLE 和一对 S3_ACCESS_KEY/S3_SECRET_KEY（或默认凭证链）。不自动创建 bucket；statement-account
显式配置实际主/子商户。
部署所需 Secret 内容及存储策略需由部署环境提供，本轮未发布或访问存储。当前未接官方账单自动下载/验签来源或 payment 权威导出。

网关和平台安装见 [Gateway API / Envoy Gateway](../deploy/gateway/README.md)，选择器与 CNI 验收见 [HTTP 网络策略](../deploy/NETWORK_POLICY.md)。
部署前使用 check-kubernetes.sh 检查客户端/服务端版本差距；deploy.sh 也执行此检查，不自动安装或升级 kubectl。

发布后的线程 A/B、池等待和业务验收见 [最终验收](../deploy/ACCEPTANCE.md)，使用记录模板保存原始证据，不把脚本 PASS 当作金融链路通过。
