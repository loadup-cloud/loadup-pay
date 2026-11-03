# 按环境维护配置

直接维护 dev、test、prod；APP_ENV 默认 dev。不使用模板生成或 init。

```text
deploy/config/dev/
  common.env                    # issuer、JWKS、token endpoint 等普通配置
  service-authorizations.json   # AuthServer 审查过的应用授权，含逻辑租户，不含密码
  authserver/config.env
  authserver/secret.env          # 数据库密码、KeyStore 密码、各调用方 Secret，忽略提交
  authserver/authserver.p12      # 持久化签名密钥，仅挂载 AuthServer，忽略提交
  acquire/config.env
  acquire/secret.env             # 业务密钥、数据库密码、本应用 SERVICE_AUTH_CLIENT_SECRET
  ...                           # 其他六个业务应用
```

环境文件使用字面 KEY=value，不加 shell 引号、不展开变量、不执行命令。应用 config.env 覆盖 common.env；普通参数和 secret.env 不得重名。

每个应用配置 `<APP>_JDBC_URL`、`<APP>_JDBC_USERNAME`、`<APP>_JDBC_PASSWORD`。共用 JWT_ISSUER_URI、JWT_JWK_SET_URI，资源 audience 仍各自独立。新部署不再使用离线 JWT 文件或静态公钥目录。

product、limitcenter、payment、acquire、reconciliation 配置 SERVICE_AUTH_CLIENT_SECRET，并在 AuthServer 的 secret.env 中配置对应 `<APP>_OAUTH2_CLIENT_SECRET`，值必须相同。共用 SERVICE_AUTH_TOKEN_URI；test/prod 使用 HTTPS，dev 显式允许本地 HTTP。SERVICE_AUTH_TENANT_ID 默认 __default__，与授权清单的逻辑租户一致。

LIMITCENTER_PERMIT_SECRET、PAYMENT_INPUT_FINGERPRINT_KEY 继续保留原值，至少 32 个 UTF-8 字节。reconciliation 仍需要私有 S3 bucket、可选成对 S3 凭证和经过授权的账户目录。这些业务配置不因 AuthServer 接入而重置。

本地执行 `./scripts/authserver.sh prepare-dev` 可以生成缺失的 OAuth 应用凭证和 PKCS12，不覆盖已有值，不创建数据库或集群资源。其他环境手动准备独立材料。详见 [AuthServer](../../authserver/README.md)。

```bash
./scripts/authserver.sh prepare-dev
# 先准备独立 loadup_authserver 数据库，并完成 framework 的本地构建安装。
APP_ENV=dev ./scripts/configmap.sh check all
APP_ENV=dev IMAGE_TAG=auth-001 ./scripts/up.sh authserver
APP_ENV=dev IMAGE_TAG=auth-002 ./scripts/up.sh all
```

配置工具按内容计算 immutable 快照版本；配置换版通过滚动重启生效，ConfigMap 不自动刷新 Bean。test/prod 集群操作还需显式指定 KUBE_CONTEXT；namespace 固定 loadup-pay，当前镜像流程仍面向 OrbStack 本地镜像，不自动推送远程镜像。

业务 schema 动态路由还未实现；业务表不加回 tenant_id，令牌和执行上下文继续保留逻辑租户身份。旧 ignored JWT 文件和已运行旧资源没有自动删除，须在完成迁移验收后退役。
