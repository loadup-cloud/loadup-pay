# 环境配置与版本化发布

配置源固定按 deploy/config/dev、test、prod 维护，不再生成模板或以发布版本命名本地目录。
APP_ENV 默认 dev；每个环境的一套配置可反复发布。配置文件格式和字段见 [环境目录](config/README.md)，命令见 [脚本说明](../scripts/README.md#环境配置与一键部署)。

## 配置与资源

common.env 只放共享普通参数（如 JWT 信任源、线程开关）；应用 config.env 合并后优先于 common.env。
secret.env 保存密码和业务密钥，service-tokens/ 保存调用方自己的有效访问令牌；二者被 Git 忽略。
公钥 jwt-public.pem 可入库，通过既有 service-tokens Secret 的目录投射到应用。私钥仅保存在本地受控目录，不挂载 Pod。

| 资源 | 名称 | 生效方式 |
| --- | --- | --- |
| 不可变 ConfigMap | <app>-config-<CONFIG_VERSION> | 包含 common.env 与应用 config.env 的合并值；新版本随滚动启动读取 |
| 不可变环境 Secret | <app>-secret-<SECRET_VERSION> | 新版本随滚动启动读取 |
| 可变投射 Secret | <app>-service-tokens | 保存 JWT 文件和可选公钥；完整目录挂载，不使用 subPath |

本地只维护环境配置；不可变快照和历史发布记录继续保留，避免原地删建破坏回退。
up.sh/configmap.sh create、update 根据选定应用的配置内容自动计算 cfg-<env>-<摘要>、sec-<env>-<摘要>。
公钥内容参与配置摘要，令牌续期不改变环境快照。可显式指定版本，但同名不同内容仍拒绝覆盖。
单应用/all 的计算范围不同，回退应使用归档中的实际版本，不凭本地当前配置重新推算旧版本。

## 日常发布

```bash
APP_ENV=dev ./scripts/configmap.sh check all
APP_ENV=dev IMAGE_TAG=release-002 ./scripts/up.sh all
# 只创建/换版配置，不立即发布：
APP_ENV=dev ./scripts/configmap.sh update all
# 先独立构建新镜像，再复用镜像发布：
IMAGE_TAG=release-003 ./scripts/build.sh all
APP_ENV=dev SKIP_BUILD=1 IMAGE_TAG=release-003 ./scripts/up.sh all
```

新公钥验签依赖本次 framework 与业务启动配置变更，需要构建新镜像；旧 release-001 不支持该模式。
首次迁移先显式执行 ./scripts/configmap.sh tokens all，为现有投射 Secret 加入公钥；dev 中保留原有效令牌和 issuer，旧应用仍可继续校验。
随后构建/发布新镜像，确认七应用及调用均已改用本地公钥后，再单独退役旧 loadup-local-jwks Deployment/Service/ConfigMap。
本次只修改源码和本地配置，没有切换集群或删除仍被旧镜像使用的 JWKS 服务。

环境目录不会改变当前 kubectl context，也不自动选择 Spring profile。dev 默认 orbstack；test/prod 的集群操作必须显式提供 KUBE_CONTEXT。
部署 namespace 仍是 loadup-pay，应使用各环境目标集群；同集群多 namespace 发布不在本入口范围内。
当前镜像流程仍是 OrbStack 本地镜像与 imagePullPolicy: Never；test/prod 配置整理不代表远程镜像发布能力已实现。

## 离线 JWT

所有应用可以使用同一发行方的 RSA 公钥验签，无需运行 JWT/JWKS 服务。
JWT_PUBLIC_KEY_LOCATION=file:/var/run/loadup/secrets/jwt-public.pem 时，issuer 仅作为声明值比较，不访问 URL。
dev 保留历史 issuer 字符串以继续使用现有令牌；该字符串不代表公钥模式下依赖旧服务。
各应用 audience 不同，调用方的 sub/service_id、权限与受众继续分开，不能用一个万能访问令牌替代。

./scripts/dev-jwt.sh 使用固定的本地 RSA 私钥按 dev/service-token-policy.json 离线签发；默认保留已有文件，RENEW=1 明确续期。
有效期默认 30 天（开发可配置 1–365 天），没有永久免校验令牌。签发只写本地文件，tokens 命令才向集群发布。
每次部署不重新生成私钥、业务密钥或令牌。test/prod 使用各自的真实发行方、密钥及权限，不复用开发私钥。

静态公钥在启动时读取，换钥需要滚动；令牌 Secret 文件续期有投射传播延迟，应用逐次打开 JWT 文件。
保留签名有效期的重叠；需要多密钥平滑轮换时使用 JWKS。ConfigMap 更新不会自动刷新 Spring Bean。

## 回退与清理

直接 deploy.sh 继续消费已有明确快照，例如：

```bash
SKIP_BUILD=1 IMAGE_TAG=release-previous CONFIG_VERSION=cfg-previous SECRET_VERSION=sec-previous \
  ./scripts/deploy.sh all
CONFIG_VERSION=cfg-unused SECRET_VERSION=sec-unused ./scripts/configmap.sh delete payment config
```

以上 previous/unused 是示例名称，应替换成归档中的真实值。回退保留当前有效服务令牌，不恢复过期凭证，也不撤销数据库迁移或已发生的金融操作。
删除前检查 Pod、Deployment、ReplicaSet（包括零副本）、StatefulSet、DaemonSet、Job、CronJob 和 RELEASE_DIR 发布记录；被引用即拒绝。
只删除明确的应用/版本，默认范围为 ConfigMap；secret、tokens、all 需明确指定。脚本不自动删除历史、不清理业务数据或存储卷。
清理不是跨资源事务，避免与发布并发执行。敏感输入经 stdin 传给 kubectl，不保存或输出 Secret 清单；服务端详情隐藏以避免回显凭证。

## 滚动与请求排空

七 Deployment 使用 maxUnavailable=0、maxSurge=1、minReadySeconds=5，并保留 5 个历史 ReplicaSet。
readiness 检查 HTTP readinessState/db，liveness 保持基础存活检查；未就绪新实例不会替换全部旧实例。
Pod 不挂载 Kubernetes ServiceAccount token，应用无需发现 API/RBAC。

preStop 在当前 Temurin 运行镜像中执行 10 秒等待，再由 JVM 接收终止信号。
本地七 JVM 并行冷启动已观察到超过 120 秒；startupProbe 当前提供 360 秒启动预算，Deployment 进度期限为 600 秒，verify.sh 等待上限为 660 秒。
首轮失败与后续版本记录见 [本地环境](LOCAL_DEVELOPMENT.md)，不修改健康检查的成功条件。
Spring 生命周期每阶段等待上限显式设为 75 秒，Pod 终止宽限为 100 秒。
该预算为端点传播、MVC 排空和普通 60 秒账单等待留出空间；读取超时不是总 deadline，多个生命周期阶段也不共用同一个 75 秒预算。
实际请求分布、长下载、后台任务、超时中断和滚动回退仍需真实验收，不承诺任意长任务可在终止前完成。
替换为无 shell/sleep 的镜像时需同步调整 preStop；见 [Pod 终止过程](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination)。

HTTP NetworkPolicy 随核心清单先于 Deployment 渲染；完整发布为 22 个资源，单应用 4 个。
Envoy Gateway 使用独立 chart，见 [网关说明](gateway/README.md) 和 [网络策略](NETWORK_POLICY.md)。本说明不代表真实隔离或业务已经验收。

## 待执行验证

test_render_deploy.py 已补版本变化、单/全应用选择、原源不变、非法版本、错误配置引用、令牌 subPath 拒绝回归源码。
test_deploy_release.py 使用伪 kubectl/docker 验证预检失败不 apply、失败 rollout 留存候选与同名冲突拒绝，不访问真实集群。
本轮未执行这些测试。后续还需在目标 Kubernetes 验证配置版本切换/回退、凭证投射轮换、多副本分布和真实业务请求排空。

线程模型切换同样通过新配置快照和滚动发布完成，不热切换执行器。默认不启用候选虚拟线程；流程与门槛见 [最终验收](ACCEPTANCE.md)。
