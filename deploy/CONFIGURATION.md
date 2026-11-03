# 环境配置与版本化发布

配置源按 deploy/config/dev、test、prod 直接维护。文件格式和必需值见 [环境目录](config/README.md)，应用授权及 KeyStore 见 [AuthServer](../authserver/README.md)。

| 资源 | 名称 | 内容与生效方式 |
| --- | --- | --- |
| 不可变 ConfigMap | `<app>-config-<CONFIG_VERSION>` | common.env 与应用 config.env；AuthServer 额外包含授权 JSON；启动读取 |
| 不可变环境 Secret | `<app>-secret-<SECRET_VERSION>` | 密码、业务密钥、OAuth 应用凭证；启动读取 |
| 不可变文件 Secret | `authserver-files-<SECRET_VERSION>` | PKCS12，只读挂载 AuthServer，内容参与 Secret 摘要 |

业务应用不再挂载 JWT 文件或私钥。调用方从 AuthServer 自动获取和续取 5 分钟服务令牌；接收方使用 JWKS 缓存公钥、本地验签，继续检查 issuer、audience、有效期、token_use、租户及权限。商户/用户委托保持原来的 Actor 流程。

## 发布

```bash
./scripts/authserver.sh prepare-dev
# 先创建 loadup_authserver 数据库，参见 scripts/init-local-databases.sql。
APP_ENV=dev ./scripts/configmap.sh check all
APP_ENV=dev IMAGE_TAG=auth-001 ./scripts/up.sh authserver
APP_ENV=dev IMAGE_TAG=auth-002 ./scripts/up.sh all
# 仅创建新配置快照：
APP_ENV=dev ./scripts/configmap.sh update payment
```

up.sh 先为选定应用计算 cfg-<环境>-<摘要> / sec-<环境>-<摘要>，再创建或复用快照，最后构建和发布。构建会先安装所需 framework 组件；全量发布先等待 AuthServer 就绪，再发布业务应用。普通配置和授权进入配置摘要；密码和 KeyStore 进入 Secret 摘要。相同内容可以复用，同名不同内容拒绝覆盖。单应用和 all 的计算范围不同，保存实际发布记录用于回退。

直接 deploy.sh/render.sh 仍要求显式版本。发布前预检所有配置引用及 immutable 属性，再检查镜像并 apply。全量清单为 25 个资源：Namespace、八组 NetworkPolicy/Deployment/HTTP Service；单应用为 4 个。

ConfigMap、环境 Secret、KeyStore 和 Spring Bean 默认滚动重启生效，不承诺热刷新。客户端 Secret 轮换需协调 AuthServer 与调用方，新旧凭证并行使用窗口当前未实现；首次迁移也必须使用支持 OAuth 取令牌的新镜像。不要用新配置启动旧 release-001。

本轮未构建或部署；不能将源码配置视为运行环境已迁移。旧 JWKS、旧服务令牌 Secret 和发布记录应在所有旧消费者退出、验收通过且回退窗口结束后人工清理。

## 回退与删除

```bash
SKIP_BUILD=1 IMAGE_TAG=previous CONFIG_VERSION=cfg-previous SECRET_VERSION=sec-previous ./scripts/deploy.sh all
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused ./scripts/configmap.sh status payment
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused ./scripts/configmap.sh delete payment config
```

使用归档中的实际引用组合；回退不会撤销数据库迁移或已经发生的金融操作。AuthServer 的授权配置在启动时同步数据库，回退旧配置可能恢复旧授权，必须先审查。密钥回退必须保留仍在有效期内令牌对应的公钥。

删除默认仅 ConfigMap，secret/all 必须明确指定。工具检查 Pod、Deployment、历史 ReplicaSet、StatefulSet、DaemonSet、Job、CronJob 与 RELEASE_DIR 归档；有引用或读取失败就拒绝删除。AuthServer 的 secret 范围同时包括对应版本的 KeyStore 文件 Secret。无强制绕过，不自动清理业务数据或存储卷；清理期间避免并发发布。

敏感输入只经 stdin 交给 kubectl，不输出 Secret 内容或服务端错误详情。环境目录不会自动切换 context；test/prod 要求明确 KUBE_CONTEXT，当前 namespace 固定 loadup-pay，镜像仍为本地 imagePullPolicy: Never。

## 健康、排空和验证

八 Deployment 沿用 HTTP readiness/liveness、360 秒启动预算、600 秒进度期限和 660 秒等待上限；maxUnavailable=0、maxSurge=1，preStop 等待 10 秒，终止宽限 100 秒。业务应用与 AuthServer 均设置 Spring 每阶段停机等待 75 秒。应用不挂载 ServiceAccount token，不访问 Kubernetes 发现 API。

NetworkPolicy 在工作负载前应用；AuthServer 只允许声明的内部应用和监控命名空间访问，没有公开网关入口。Gateway API + Envoy Gateway 仍单独发布，参见 [网关说明](gateway/README.md)。

Java 授权链路、客户端缓存续取和脚本配置/渲染回归测试源码已更新，未执行。后续需验证真实 PostgreSQL、KMS、跨进程 JWKS、权限失败、凭证/密钥轮换、多副本、排空、发布回退和金融业务请求。Pod 就绪不代表这些验收通过。
