# 版本化配置与发布

七应用继续使用 Spring Boot 原生外部配置与 Kubernetes Service DNS，不增加配置监听器或注册中心。
以下是发布源码和操作说明；本轮未构建、运行测试、访问集群或部署。

## 三类配置资源

| 资源 | 名称 | 生效与用途 |
| --- | --- | --- |
| ConfigMap | `<app>-config-<CONFIG_VERSION>` | `immutable: true`；普通环境配置，创建新版本并滚动发布 |
| Secret | `<app>-secret-<SECRET_VERSION>` | `immutable: true`；数据库密码等环境变量，随 Pod 创建读取 |
| Secret | `<app>-service-tokens` | 可变；只读完整目录挂载 `/var/run/loadup/secrets`，保存逐次读取的服务凭证文件 |

CONFIG_VERSION、SECRET_VERSION 必须显式提供，均为最多 32 字符的小写 DNS label，例如 cfg-20261103、sec-20261103。
同一版本下每应用各有独立资源；单应用发布可以选择自己的配置版本。不得修改已有快照，或删除后重建并复用同一版本名。
旧镜像、ConfigMap、环境 Secret 至少保留到对应发布版本退出回退窗口；脚本不自动清理它们。

源 pay.yml 中 `<app>-config` / `<app>-secret` 是渲染占位引用，不能直接 apply。
渲染器替换为具体版本，同时将镜像、配置和环境 Secret 版本写入 Pod template annotation。
任一版本变化都会改变 Pod template，由 Deployment 的滚动更新处理。

普通配置不得含密码、私钥或 token。环境 Secret 不再同时用于服务令牌文件挂载，令牌也不通过 envFrom 导入。
没有出站文件凭证的服务可准备空的 `<app>-service-tokens`；有出站调用的服务按目标文件名提供有效、匹配权限/租户/受众的凭证。
凭证发行、续期和投递由部署环境负责，仓库不生成凭证。

## 准备快照

由发布者先准备 namespace、配置资源和服务令牌 Secret。下面仅以 payment 示意，文件内容来自受控配置管理；
不是应用构建步骤，也不会由 deploy.sh 自动执行：

```bash
# 普通环境变量文件只含非敏感配置。
kubectl --context orbstack -n loadup-pay create configmap payment-config-cfg-20261103 \
  --from-env-file=/secure/payment-config.env --dry-run=client -o yaml |
  kubectl patch --local -f - --type=merge -p '{"immutable":true}' -o yaml |
  kubectl --context orbstack -n loadup-pay create -f -

# 敏感环境变量来自受限文件；命令参数不包含密码值。
kubectl --context orbstack -n loadup-pay create secret generic payment-secret-sec-20261103 \
  --from-env-file=/secure/payment-secret.env --dry-run=client -o yaml |
  kubectl patch --local -f - --type=merge -p '{"immutable":true}' -o yaml |
  kubectl --context orbstack -n loadup-pay create -f -
```

这些管道应在启用 pipefail 的 shell 执行；不要保存或打印敏感管道输出。实际 namespace/context 和受控文件由发布者指定。
快照使用 create，不覆盖已有版本。令牌 Secret 的 key 例如 acquire.jwt、realtime.jwt、limitcenter.jwt，
由实际调用方配置的 token-file 决定；不要把示例名当成权限授权。

## 预览与发布

```bash
# 仅渲染供审查的引用清单：不构建、不 apply、不读取配置/Secret 内容。
IMAGE_TAG=release-001 CONFIG_VERSION=cfg-20261103 SECRET_VERSION=sec-20261103 \
  ./scripts/render.sh all > /tmp/pay-release-001.json

# 构建并发布。此命令由发布者显式执行。
IMAGE_TAG=release-001 CONFIG_VERSION=cfg-20261103 SECRET_VERSION=sec-20261103 \
  ./scripts/deploy.sh all

# 复用既有镜像；单应用使用 payment，其余资源不重新应用。
SKIP_BUILD=1 IMAGE_TAG=release-001 CONFIG_VERSION=cfg-20261103 SECRET_VERSION=sec-20261103 \
  ./scripts/deploy.sh payment
```

render.sh 使用 kubectl 的 client dry-run 读取源清单，仍需要可用的 kubectl/context；不承诺无 discovery 缓存时完全离线。
deploy.sh 先检查 kubectl/服务端版本差距，再在任何 apply 之前完整渲染，并检查引用资源的存在性和 immutable 字段。
版本 ConfigMap/环境 Secret 必须不可变，投射令牌 Secret 必须保持可变，缺失或不符合要求立即失败；脚本不解析、打印或归档 data/stringData。
kubectl 需要读取相应对象的权限，原生 get 响应可能包含完整对象；只将所需字段输出给脚本，不把 Secret JSON 写入临时文件。
预检发生在构建前，避免缺配置时才开始构建镜像。配置值、JWT 权限与外部依赖仍由应用和真实环境验收负责。

选定镜像确认存在后，脚本在 deploy/releases/ 保存完整渲染结果，只包含模板及配置引用。
文件名绑定 scope、IMAGE_TAG、CONFIG_VERSION、SECRET_VERSION；同名不同内容拒绝覆盖或 apply。
RELEASE_DIR 可覆盖归档位置，该目录默认不进入 Git。归档在 apply 前保存，即使 rollout 失败也保留候选记录，不能作为发布成功证明。
生产应另行保存实际集群、镜像 digest、成功状态和验收结果；现有脚本仍是 OrbStack 本地镜像发布工具。

## 生效、轮换与回退

ConfigMap/Secret 的环境变量在新 Pod 启动时读取，更新对象不刷新已有 Bean。
版本引用变化触发重建，配置中心热刷新不是默认能力；见 [Kubernetes ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/)。

可变令牌 Secret 通过完整目录投射，不使用 subPath。Kubernetes 的文件更新有传播延迟，不保证立即生效；
应用每次打开 token-file，可在文件更新后观察新凭证，但不会刷新其他 Bean。
发行方应保持有效期重叠，避免旧凭证失效早于新文件可见；见 [Secret 更新规则](https://kubernetes.io/docs/concepts/configuration/secret/#using-secrets-as-files-from-a-pod)。

回退时使用保留的匹配 IMAGE_TAG、CONFIG_VERSION 和 SECRET_VERSION，以 SKIP_BUILD=1 重新执行相同 scope 发布。
服务令牌仍使用当前有效的可变投射 Secret，不回退到已过期 token。
也可由发布者审查并使用已归档的匹配清单；须先检查其配置引用、镜像和目标 context。
配置回退不撤销已经执行的数据库迁移或外部金融操作；旧环境凭证也必须仍能访问其目标。

## 滚动与请求排空

七 Deployment 使用 maxUnavailable=0、maxSurge=1、minReadySeconds=5，并保留 5 个历史 ReplicaSet。
readiness 检查 HTTP readinessState/db，liveness 保持基础存活检查；未就绪新实例不会替换全部旧实例。
Pod 不挂载 Kubernetes ServiceAccount token，应用无需发现 API/RBAC。

preStop 在当前 Temurin 运行镜像中执行 10 秒等待，再由 JVM 接收终止信号。
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
