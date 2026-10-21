# 构建与部署脚本

用于六个应用的本地JAR、Docker镜像和OrbStack Kubernetes部署。每个应用目录提供`build.sh`与`deploy.sh`，入口委托本目录的公共实现。

| 入口                                | 行为                                                  |
|-------------------------------------|-------------------------------------------------------|
| `./scripts/build.sh all`            | 一次Maven构建六应用及其依赖，然后构建六个镜像         |
| `./scripts/deploy.sh all`           | 构建JAR/镜像、部署六应用、等待就绪端点                |
| `./payment/build.sh`                | 仅构建payment及其必需依赖，生成payment镜像            |
| `./payment/deploy.sh`               | 构建并部署payment，不修改其他五个应用                 |
| `./pay-common/build.sh`             | 仅构建公共库JAR，不要求Docker或Kubernetes，不生成镜像 |
| `./scripts/verify.sh [all或应用名]` | 检查选定Deployment与Service ready EndpointSlice       |

可选应用名：`acquire`、`payment`、`customer`、`product`、`limitcenter`、`realtime`
。所有入口均可从其他工作目录调用。client/domain/pay-common作为应用依赖参与构建，不单独生成部署镜像。

## 前提与默认行为

- Java 25：优先使用项目`mise.toml`已安装的运行时；无mise时使用`JAVA_HOME`/PATH并校验主版本。不自动下载Java，也不回退到Java
  21。
- 使用`mvnd clean package -pl <选定bootstrap列表> -am -Dmaven.test.skip=true`
  ；全量应用只执行一次Maven，不逐应用重复清理共享依赖。默认不编译或执行测试，构建通过不等于单测通过。
- Docker和Kubernetes默认context均为`orbstack`，脚本通过显式context调用，不更改全局当前context。部署固定namespace为
  `loadup-pay`。
- 使用本地共享镜像及`imagePullPolicy: Never`；不会推送镜像。其他集群需另外实现镜像发布并修改拉取策略。
-
默认镜像标签为Asia/Shanghai时间戳与进程ID，每次发布生成新标签。构建拒绝覆盖已有同名镜像，避免同标签重建后Pod仍使用旧镜像；复用已有镜像请使用SKIP_BUILD。源码清单不写入动态标签；部署时在内存中选择资源并绑定镜像。
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
经kubectl本地dry-run输出的JSON：全量选择13个资源（namespace及六组Deployment/Service），单应用选择3个资源。单应用发布不会重新应用其他应用的镜像或副本配置。全部资源校验完成后才输出，管道启用pipefail；构建或镜像缺失时不提交部署。

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


acquire 普通创单业务包使用 `./acquire/build.sh -Pacquire-business`，运行时同时设置 `SPRING_PROFILES_ACTIVE=acquire-business`；独立数据库、JWT 与 product HTTP 配置见[acquire说明](../acquire/README.md)。现有部署清单仍需业务 Secret/ConfigMap 覆盖层，本轮未构建或部署该业务镜像。

acquire-business 现还需注入 `ACQUIRE_CHECKOUT_PAGE_URL` 与外部 Secret `ACQUIRE_CHECKOUT_SECRET`，并同源路由 `/checkout`、`/checkout.js`、`/checkout.css` 和 `/api/cashier/order/summary`。仅提供会话签发及只读摘要，不代表付款链路已部署。


payment 业务 JAR/镜像使用 `./payment/build.sh -Ppayment-business`，运行时启用同名 Spring profile 并注入 PAYMENT_JDBC_*、PAYMENT_JWT_* 和可选 PAYMENT_ACQUIRE_SERVICE_ID。当前受理只持久化 INIT，未对接渠道；现有部署清单仍需配置覆盖层，详见[payment说明](../payment/README.md)。本批仅完成窄范围源码/测试源码编译，没有打包部署业务镜像。
