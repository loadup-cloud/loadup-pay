# 按环境维护配置

直接维护 dev、test、prod；APP_ENV 默认 dev。不使用模板生成或 init。

```text
deploy/config/dev/
  common.env                    # issuer、JWKS、token endpoint 等普通配置
  oauth2-server/config.env
  oauth2-server/secret.env       # 数据库密码、KeyStore 密码，忽略提交
  oauth2-server/oauth2-server.p12 # 持久化签名密钥，仅挂载 OAuth server，忽略提交
  acquire/config.env
  acquire/secret.env             # 业务密钥、数据库密码、本应用 SERVICE_AUTH_CLIENT_SECRET
  ...                           # 其他六个业务应用
```

Admin 在各自 `config.env` 中覆盖 common.env 的 `JWT_ISSUER_URI` 并设置 `JWT_AUDIENCE=admin`，发布仅用于 OAuth 管理的操作员 JWT；它通过 `OAUTH_MANAGEMENT_BASE_URL` 调用内部 OAuth server。OAuth server 使用独立的 `OAUTH_MANAGEMENT_ISSUER_URI`、`OAUTH_MANAGEMENT_AUDIENCE` 和 `OAUTH_MANAGEMENT_JWK_SET_URI` 校验该 JWT。三个环境的操作员 issuer 配置相互匹配，且不同于其他服务使用的公共 `JWT_ISSUER_URI`。

环境文件使用字面 KEY=value，不加 shell 引号、不展开变量、不执行命令。应用 config.env 覆盖 common.env；普通参数和 secret.env 不得重名。

每个应用配置 `<APP>_JDBC_URL`、`<APP>_JDBC_USERNAME`、`<APP>_JDBC_PASSWORD`。共用 JWT_ISSUER_URI、JWT_JWK_SET_URI；入站 audience 取 `spring.application.name`，出站目标按应用名约定 `http://<app>:8080`，因此不再需要 `<APP>_JWT_AUDIENCE` 或 `*_BASE_URL`。特殊地址在应用配置中单独覆盖。新部署不再使用离线 JWT 文件或静态公钥目录。

productcenter、limitcenter、payment、acquire、reconciliation 配置 SERVICE_AUTH_CLIENT_SECRET，并在 OAuth server 数据库预置对应 RegisteredClient。客户端密钥由环境 secret 提供，数据库中的客户端记录必须预先存在；启动不会导入或覆盖客户端。共用 SERVICE_AUTH_TOKEN_URI；test/prod 使用 HTTPS，dev 显式允许本地 HTTP。

OAuth 客户端管理只通过 Admin BFF：`POST /api/oauth/clients/{registrationId}/{create|detail|authorization|disable|expiry|rotate}`。Admin 原样转发操作员 Bearer 到 OAuth server 内部 `POST /api/internal/v1/oauth/clients/...`。双方都要求 `principal_type=OPERATOR` 和对应的 `oauth:client:create|read|authorize|disable|expire|rotate` 权限。OAuth server 不通过公网 Gateway 暴露管理端点；Admin 不连接 OAuth 数据库。管理请求不自动重试；创建或轮换响应丢失时结果未知，应查询详情并显式执行凭证轮换恢复。

LIMITCENTER_PERMIT_SECRET、PAYMENT_INPUT_FINGERPRINT_KEY 继续保留原值，至少 32 个 UTF-8 字节。reconciliation 仍需要私有 S3 bucket、可选成对 S3 凭证和经过授权的账户目录。这些业务配置不因 OAuth server 接入而重置。

dev 的 `up.py oauth2-server` / `up.py all` 会自动生成缺失的调用方凭证和 PKCS12，不覆盖已有值，不创建数据库或集群资源。需先在 OAuth server 数据库中创建对应客户端记录；其他环境手动准备独立材料。

```bash
# 先准备 OAuth server 数据库并预置 RegisteredClient 客户端记录，并完成 framework 的本地构建安装。
APP_ENV=dev python3 scripts/configmap.py check all
APP_ENV=dev IMAGE_TAG=oauth-001 python3 scripts/up.py oauth2-server
APP_ENV=dev IMAGE_TAG=auth-002 python3 scripts/up.py all
```

配置工具按内容计算 immutable 快照版本；配置换版通过滚动重启生效，ConfigMap 不自动刷新 Bean。test/prod 集群操作还需显式指定 KUBE_CONTEXT；namespace 固定 loadup-pay，当前镜像流程仍面向 OrbStack 本地镜像，不自动推送远程镜像。

每套部署必须在 `config.env` 显式提供 `DEPLOYMENT_CODE` 与 `DEPLOYMENT_DATABASE_SCHEMA`；应用配置不提供默认值，缺失时启动失败。业务表不含 tenant_id，隔离由固定 schema 和数据库权限保证。旧 ignored JWT 文件和已运行旧资源没有自动删除，须在完成迁移验收后退役。
