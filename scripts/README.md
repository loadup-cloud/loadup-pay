# 构建与部署脚本

用于七个应用的本地JAR、Docker镜像和OrbStack Kubernetes部署。每个应用目录提供`build.sh`与`deploy.sh`，入口委托本目录的公共实现。

| 入口                                | 行为                                            |
|-------------------------------------|-------------------------------------------------|
| `./scripts/build.sh all`            | 一次Maven构建七应用及其依赖，然后构建七个镜像   |
| `./scripts/deploy.sh all`           | 构建JAR/镜像、部署七应用、等待就绪端点          |
| `./payment/build.sh`                | 仅构建payment及其必需依赖，生成payment镜像      |
| `./payment/deploy.sh`               | 构建并部署payment，不修改其他六个应用           |
| `./scripts/verify.sh [all或应用名]` | 检查选定Deployment与Service ready EndpointSlice |

可选应用名：`acquire`、`payment`、`customer`、`product`、`limitcenter`、`realtime`、`reconciliation`
。所有入口均可从其他工作目录调用。client/domain/rpc作为应用依赖参与构建，不单独生成部署镜像。

## 前提与默认行为

- Java 25：优先使用项目`mise.toml`已安装的运行时；无mise时使用`JAVA_HOME`/PATH并校验主版本。不自动下载Java，也不回退到Java
  21。
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
IMAGE_TAG=release-001 ./scripts/deploy.sh all

# 分开构建、部署，第二步复用同一批本地镜像
IMAGE_TAG=release-002 ./scripts/build.sh all
SKIP_BUILD=1 IMAGE_TAG=release-002 ./scripts/deploy.sh all

# 单应用发布
./acquire/deploy.sh

# 显式要求编译并执行所选应用及依赖的测试
./payment/build.sh -Dmaven.test.skip=false
```

支持环境变量`IMAGE_TAG`、`IMAGE_PREFIX`（默认`loadup-pay`）、`DOCKER_CONTEXT`与`KUBE_CONTEXT`。两端必须使用能访问同一批本地镜像的环境；
`SKIP_BUILD=1`必须指定已构建的IMAGE_TAG，脚本在应用资源前检查选定镜像存在。

`render-deploy.py`只处理`deploy/pay.yml`
经kubectl本地dry-run输出的JSON：全量选择22个资源（namespace及七组Deployment/HTTP Service/Dubbo Triple Headless
Service），单应用选择4个资源。Headless 名称固定为 `<app>-headless`，selector 与 Pod 的 app 标签一致，端口为
50051；渲染器拒绝缺失、重复或不符合约定的 Headless 资源。单应用发布不会重新应用其他应用的镜像或副本配置。全部资源校验完成后才输出，管道启用pipefail；构建或镜像缺失时不提交部署。

## 验收范围

部署脚本等待每个选定Deployment
rollout与ready端点。Kubernetes中的startup/liveness/readiness调用Actuator基础健康入口。脚本不创建业务订单或调用测试RPC，也不部署Headlamp；Headlamp仍由独立
`headlamp.sh`管理。

已部署版本、实际检查结果与未执行项目见[实施说明](../IMPLEMENTATION.md)。

## Customer 业务镜像

`./customer/build.sh -Pcustomer-business` 可选择新增业务装配；默认构建继续生成基础骨架。业务运行需要激活同名 Spring
profile，并注入数据库/JWT配置，见[customer说明](../customer/README.md)。现有 deploy/pay.yml 未包含这些配置，业务镜像须准备相应配置覆盖层后再发布。

## Product 业务镜像

`./product/build.sh -Pproduct-business` 选择准入业务装配；运行时需同名Spring
profile、product数据库/JWT及customer业务地址与委托audience。现有清单未注入这些配置，发布前准备配置覆盖层；参见[product说明](../product/README.md)
。本批没有自动生成额度激活回执或发布真实合约。

limitcenter 业务 JAR/镜像可沿用 `./limitcenter/build.sh -Plimitcenter-business`
；需另行注入数据库、签发方和同名运行时profile，见[业务说明](../limitcenter/README.md)。默认部署清单仍为基础骨架，本批没有发布业务镜像。

acquire 普通创单业务包使用 `./acquire/build.sh -Pacquire-business`，运行时同时设置
`SPRING_PROFILES_ACTIVE=acquire-business`；独立数据库、JWT 与 product gRPC 配置见[acquire说明](../acquire/README.md)
。现有部署清单仍需业务 Secret/ConfigMap 覆盖层，本轮未构建或部署该业务镜像。

acquire-business 现还需注入 `ACQUIRE_CHECKOUT_PAGE_URL` 与外部 Secret `ACQUIRE_CHECKOUT_SECRET`，并同源路由 `/checkout`、
`/checkout.js`、`/checkout.css` 和 `/api/cashier/order/summary`。仅提供会话签发及只读摘要，不代表付款链路已部署。

payment 业务 JAR/镜像使用 `./payment/build.sh -Ppayment-business`，运行时启用同名 Spring profile 并注入 PAYMENT_JDBC_
*、PAYMENT_JWT_* 和可选 PAYMENT_ACQUIRE_SERVICE_ID。当前受理只持久化
INIT，未对接渠道；现有部署清单仍需配置覆盖层，详见[payment说明](../payment/README.md)。本批仅完成窄范围源码/测试源码编译，没有打包部署业务镜像。

acquire-business 付款发起/结果刷新还需
PAYMENT_JWT_AUDIENCE、ACQUIRE_PAYMENT_TENANT_ID、ACQUIRE_SERVICE_ID（默认 acquire）和
ACQUIRE_PAYMENT_TOKEN_FILE。服务 JWT 由外部 Secret 文件挂载并轮换，需包含受信 SERVICE/sub/service_id/tenant_id/aud 与 pay:
payment:accept/read 权限；不把商户 JWT 用于 payment 内部接口。部署清单尚未补业务覆盖层，本批未打包或部署。最新
outcome/refresh 源码按用户要求暂不检查或构建。

acquire 显式关单继续使用同一挂载服务 JWT，新增需要 pay:payment:close、pay:payment:fence 和 pay:payment:fence:read 权限；固定
payment gRPC 客户端已增加 close/fence-parent/parent-fence-status 操作。新增 order_close_intent
迁移，服务实际8表。当前仍需业务配置覆盖层，本批没有打包、部署或执行迁移。

acquire-business 新增订单延期入口，商户凭证需 pay:acquire:extend 权限；无新增出站服务配置。新增
V20261022070000__order_extension_receipt.sql，实际9表。未在本批打包部署或执行迁移，自动到期任务尚未启用。

LimitCenter 额度终态业务新增 payment 事实 gRPC 客户端，配置
PAYMENT_JWT_AUDIENCE、LIMITCENTER_PAYMENT_TENANT_ID、LIMITCENTER_SERVICE_ID 与
LIMITCENTER_PAYMENT_TOKEN_FILE；令牌挂载文件需 pay:payment:limit-facts。payment 使用 PAYMENT_LIMITCENTER_SERVICE_ID
限定读取者（默认 limitcenter）。新 V20261022080000 尚未执行，镜像/业务部署仍未运行。

payment-business 的短期输入准备新增必需环境 PAYMENT_INPUT_FINGERPRINT_KEY（至少32随机UTF-8字节）、PAYMENT_KMS_ENDPOINT（HTTPS
origin）和运行 Secret PAYMENT_KMS_TOKEN。默认 Transit key 名 payment-input，须在 OpenBao 预先创建并授予
encrypt/decrypt；框架管理权限关闭。输入 TTL 默认为120秒；默认 fingerprint map key=v1，轮换同时覆盖 active id 与保留旧 key 的
map。acquire SERVICE JWT 新增 pay:payment:input:prepare 权限。新 payment_input 迁移和相关业务镜像部署尚未执行。

渠道绑定管理新增 PAYMENT_CHANNEL_MANAGEMENT_SERVICE_ID（默认 pay-ops）。该指定 SERVICE 的 JWT 按需授予 pay:payment:binding:
publish/suspend/revoke/read；不授予商户或 acquire 运维权限。新三表迁移 V20261022100000
尚未执行，渠道真实进件/Secret/产品开通校验尚待实现，ACTIVE 配置不能作为渠道运行验收证据。

支付额度出站新增 PAYMENT_LIMITCENTER_TENANT_ID、PAYMENT_SERVICE_ID（默认
payment）、LIMITCENTER_JWT_AUDIENCE、PAYMENT_LIMITCENTER_TOKEN_FILE、LIMITCENTER_BASE_URL；挂载令牌需 pay:limit:
reserve/execute/verify，不能用商户 JWT。内部首笔尝试接口另要求 acquire SERVICE 的 pay:payment:attempt:start。新增
V20261022110000 未执行；提交器和自动调度尚未接通，业务镜像部署未运行。

## 应用间 Dubbo Triple 发布

七应用 `application.yml` 固定 HTTP 8080、Triple 50051、`shutdown: graceful`，Pod 终止窗口 45 秒。消费端
`@DubboReference` 直接使用 `tri://<peer>-headless:50051`，字段类型是生成的 Protobuf IDL 接口；`dubbo.registry.address: N/A`，
`consumer.check: false`、`timeout: 3000`、`retries: 0`。Java 代码中不写环境地址，也没有应用间 HTTP 地址或降级路径。

业务镜像构建须选择相应 `-P<app>-business`，运行时启用同名 Spring profile，并准备该应用的数据库、JWT、挂载 SERVICE token 和生产
TLS 配置。默认骨架构建不包含业务 adapter，也没有 Triple 监听；只声明 Headless Service 与 containerPort 不会启动监听。

`pay.yml` 保留 HTTP ClusterIP Service 供外部 API/Actuator 使用，应用间只走 Dubbo Triple Headless Service。`verify.sh` 目前仅检查
Deployment 与 HTTP ready EndpointSlice。业务发布还需从调用方以真实 JWT/TLS 发起 Triple
调用，验证原请求幂等重放、错误映射、至少两个副本上的请求分布及滚动期间行为；不可把 DNS/HTTP 健康当作 RPC 验收。

双向依赖链在首次部署时须先就绪全部提供方，再进行真实业务验收。现有协议切换不保留内部 HTTP 或 gRPC 兼容通道；旧镜像与新
Dubbo Triple 镜像不能混用，统一切换整套业务部署。失败时恢复上一套匹配镜像及配置，不单独回滚一侧协议。

协议与调用关系见[RPC 规范](../design/RPC.md)；binder 设计与配置见
[Dubbo binder](../loadup-framework/loadup-components/integration/loadup-components-rpc/loadup-components-rpc-binder-dubbo/README.md)。

渠道插件由 payment-business 装配：channel-api/core/alipay/wechat 是普通库模块，不生成独立镜像/Service。原 payment 构建脚本的
-am 会随依赖包含这些模块；无需新增渠道部署脚本。本批未执行构建或部署。支付宝启用配置迁移为 loadup.pay.channel.alipay，支付宝退款
Provider 源码已补；可选 payment-alipay profile 配置付款码/JSAPI、支付查单/关闭与退款 HTTP 操作，真实资金链路未验收。

## 托管收银台浏览器核心测试源码

H5 页面核心使用原生 ES module/BigInt，无新增前端依赖。用户恢复验证后，可在 loadup-pay 根目录单独执行：

```bash
node --test acquire/acquire-adapter/src/test/javascript/checkout-core.test.mjs
```

本轮没有执行此命令。该测试只覆盖纯逻辑，不替代 Java 生成代码/事务、浏览器/CSP/标签页存储、可信代理、渠道域名及真实 H5
跳转验收。静态页面与 /checkout.js、/checkout-core.mjs、/checkout.css 通过独立 GET 安全链公开；原金融 POST 仍使用逐次校验的会话能力。

## Reconciliation 业务镜像

`./reconciliation/build.sh -Preconciliation-business` 选择独立对账装配，运行时启用同名 Spring profile。
配置独立 RECONCILIATION_JDBC_*、RECONCILIATION_JWT_* 和受控账户目录，详见 [对账服务](../reconciliation/README.md)。
七应用清单、镜像允许路径、选择器及 Headless 命名已同步为源码；本轮未构建镜像或运行渲染、部署、就绪脚本。
当前批次/查询/核对源码不包含已完成的实际账单下载与权威数据导入；不能将 Pod 就绪当成对账业务验收。

Reconciliation 手工文件导入已接 File/Transfer/S3 的业务装配源码；除 JDBC/JWT/账户目录，还需私有 RECONCILIATION_S3_BUCKET，
可选 REGION/ENDPOINT/PATH_STYLE 和一对 S3_ACCESS_KEY/S3_SECRET_KEY（或默认凭证链）。不自动创建 bucket；statement-account
显式配置实际主/子商户。
源码部署清单没有注入这些业务 Secret 或存储策略，本轮未发布或访问存储。当前未接官方账单自动下载/验签来源或 payment 权威导出。
