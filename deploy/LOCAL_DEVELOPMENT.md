# 本地开发环境

本说明对应 2026-11-03 的 orbstack 环境。业务镜像使用 release-001，单元测试按用户要求跳过；平台就绪不代表金融链路验收通过。

后续源码已改为 [dev/test/prod 环境配置](config/README.md)，dev 复用本机已有密钥和有效 JWT，并增加本地公钥验签。
新模式不再需要 JWT/JWKS 服务；旧运行中的 release-001 仍依赖下文记录的 JWKS，须先构建发布新镜像再退役服务。
本文下方为原发布记录，不代表本次配置重构已经部署或验收。

## 基础服务与配置

- 数据库复用本机 Docker 的 PostgreSQL，地址 postgres.orb.local:5432。七应用各自使用 loadup_<app> 数据库；发布时由 Flyway 校验已有 public schema，不清空数据库。
- S3 复用用户已有的 RustFS Docker 容器，地址 http://rustfs.orb.local:9000，启用 path-style，开发 bucket 为 loadup-pay-reconciliation-local。
  访问凭证从现有容器读取，仅保存到本地忽略目录和 Kubernetes Secret；不在本文或模板中记录。
- 本次未继续使用 S3Mock；临时 Deployment/Service/NetworkPolicy 已移除，曾创建的 loadup-local-s3 PVC 保留，不删除其数据。
- JWT 信任源为 loadup-local-jwks.loadup-pay.svc.cluster.local:8080。Nginx 只提供 /.well-known/jwks.json，不提供登录或令牌签发 API。
  所有应用仍启用完整 JWT 校验，issuer 与 JWK Set URI 指向该本地服务。

公共 JWKS ConfigMap 由本地 RSA 公钥生成，私钥不挂载到 Pod。
私钥及令牌元数据保存在 deploy/local-config/local-development-pki/，业务密钥和服务 JWT 位于对应的本地配置目录；整个 local-config/ 已被 Git 忽略。
已生成 8 份按调用方、目标受众、__default__ 租户与明确权限限定的 SERVICE JWT，有效期 30 天；到期前需重新签发并更新可变 service-tokens Secret。
本地业务 HMAC/指纹密钥首次随机生成后保持稳定，不在每次发布时重新生成。

原 deploy/local-development.yml 的公共 JWKS 服务清单已在公钥模式重构中移除；运行资源待新镜像迁移后单独退役。
现有 Docker PostgreSQL、RustFS 的容器与数据卷仍由本机维护。

## 发布版本与启动预算

首轮 cfg-20261103 / sec-20261103 已完成资源预检，但七个 JVM 同时冷启动耗时超过原 120 秒探针预算，容器被 Kubernetes 重启，首轮未通过 rollout。
原配置和候选清单均保留；随后使用 cfg-20261103-r2 / sec-20261103 发布同一批 release-001 镜像。

启动探针每 2 秒检查、最多失败 180 次，冷启动预算为 360 秒；Deployment progressDeadlineSeconds 为 600 秒，发布等待上限为 660 秒。
这些是当前本地冷启动预算，健康检查仍检查实际 liveness/readiness/db；不以放宽健康结果替代启动成功。
首轮从未就绪的旧 ReplicaSet 已缩为 0，保留 ReplicaSet/配置/镜像记录，不删除数据库、密钥或业务数据。

复用已构建镜像的发布入口：

```bash
SKIP_BUILD=1 IMAGE_TAG=release-001 CONFIG_VERSION=cfg-20261103-r2 SECRET_VERSION=sec-20261103 \
  ./scripts/deploy.sh all
```

首次构建已执行窄范围的七个 bootstrap 及其依赖 clean package，没有执行测试；同一镜像标签不重建覆盖。
发布清单保存在忽略目录 deploy/releases/，记录只含配置引用，不含 Secret 数据。

## 本次发布结果

第二轮发布脚本正常退出：七应用均为 release-001，ConfigMap 为各应用的 cfg-20261103-r2，环境 Secret 为 sec-20261103；
七个 Deployment 均 1/1 Available，七个 Service 均有就绪 EndpointSlice。新副本没有重启，数据库连接与已有 Flyway migration 校验成功。

已完成的只读检查：

- customer 的 /actuator/health/readiness 返回 200。
- 经 Envoy Gateway 访问 HTTPS /checkout 与 /checkout.js 均返回 200，使用本地证书进行 TLS 校验。
- 在 reconciliation 容器中，使用已部署的 S3 凭证对 RustFS 开发 bucket 发起签名 HEAD，返回 200。
- 9 条 HTTPRoute 均 Accepted=True / ResolvedRefs=True，原 reconciliation 缺少后端的问题已解除。
- 七个应用入站 NetworkPolicy 已存在；这只证明对象已部署，尚未覆盖所有允许/拒绝或跨租户隔离测试。

这些检查不创建订单、付款、退款或对账批次，不替代真实 JWT 授权矩阵、业务幂等、渠道回调、故障恢复、压测和配置回退验收。

网关入口、证书与本机 hosts 配置见 [Envoy Gateway](gateway/README.md)；完整业务与压测条件仍按 [最终验收](ACCEPTANCE.md) 处理。
