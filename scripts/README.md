# 构建与部署脚本

用于六个应用的本地JAR、Docker镜像和OrbStack Kubernetes部署。每个应用目录提供`build.sh`与`deploy.sh`，入口委托本目录的公共实现。

| 入口 | 行为 |
|---|---|
| `./scripts/build.sh all` | 一次Maven构建六应用及其依赖，然后构建六个镜像 |
| `./scripts/deploy.sh all` | 构建JAR/镜像、部署六应用、等待就绪端点 |
| `./payment/build.sh` | 仅构建payment及其必需依赖，生成payment镜像 |
| `./payment/deploy.sh` | 构建并部署payment，不修改其他五个应用 |
| `./pay-common/build.sh` | 仅构建公共库JAR，不要求Docker或Kubernetes，不生成镜像 |
| `./scripts/verify.sh [all或应用名]` | 检查选定Deployment与Service ready EndpointSlice |

可选应用名：`acquire`、`payment`、`customer`、`product`、`limitcenter`、`realtime`。所有入口均可从其他工作目录调用。client/domain/pay-common作为应用依赖参与构建，不单独生成部署镜像。

## 前提与默认行为

- Java 25：优先使用项目`mise.toml`已安装的运行时；无mise时使用`JAVA_HOME`/PATH并校验主版本。不自动下载Java，也不回退到Java 21。
- 使用`mvnd clean package -pl <选定bootstrap列表> -am -Dmaven.test.skip=true`；全量应用只执行一次Maven，不逐应用重复清理共享依赖。默认不编译或执行测试，构建通过不等于单测通过。
- Docker和Kubernetes默认context均为`orbstack`，脚本通过显式context调用，不更改全局当前context。部署固定namespace为`loadup-pay`。
- 使用本地共享镜像及`imagePullPolicy: Never`；不会推送镜像。其他集群需另外实现镜像发布并修改拉取策略。
- 默认镜像标签为Asia/Shanghai时间戳与进程ID，每次发布生成新标签。构建拒绝覆盖已有同名镜像，避免同标签重建后Pod仍使用旧镜像；复用已有镜像请使用SKIP_BUILD。源码清单不写入动态标签；部署时在内存中选择资源并绑定镜像。
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

支持环境变量`IMAGE_TAG`、`IMAGE_PREFIX`（默认`loadup-pay`）、`DOCKER_CONTEXT`与`KUBE_CONTEXT`。两端必须使用能访问同一批本地镜像的环境；`SKIP_BUILD=1`必须指定已构建的IMAGE_TAG，脚本在应用资源前检查选定镜像存在。

`render-deploy.py`只处理`deploy/pay.yml`经kubectl本地dry-run输出的JSON：全量选择13个资源（namespace及六组Deployment/Service），单应用选择3个资源。单应用发布不会重新应用其他应用的镜像或副本配置。全部资源校验完成后才输出，管道启用pipefail；构建或镜像缺失时不提交部署。

## 验收范围

部署脚本等待每个选定Deployment rollout与ready端点。Kubernetes中的startup/liveness/readiness调用Actuator基础健康入口。脚本不创建业务订单或调用测试RPC，也不部署Headlamp；Headlamp仍由独立`headlamp.sh`管理。

已部署版本、实际检查结果与未执行项目见[实施说明](../IMPLEMENTATION.md)。
