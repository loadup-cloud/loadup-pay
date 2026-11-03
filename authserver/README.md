# AuthServer

独立 Spring Boot 4.1 应用，面向应用间的 OAuth2 `client_credentials` 授权。签名使用框架 AuthServer SAS binder → `KmsTemplate` → kms-local；RSA 私钥仅由 AuthServer 进程加载。业务资源服务器从 JWKS 获取公钥并在本地验签。

| 能力 | 当前实现 |
| --- | --- |
| 应用认证 | 独立 client ID，`client_secret_basic`，数据库中保存 BCrypt 哈希 |
| 应用授权 | 一条注册绑定逻辑租户、调用方、目标 audience、允许的 permissions |
| 标准协议 | `/oauth2/token`、`/oauth2/jwks`、OAuth2 元数据；仅 client_credentials，不开放用户登录/OIDC |
| JWT | RS256、固定 KMS name/version、稳定 kid；5 分钟有效期 |
| 持久化 | `JdbcRegisteredClientRepository`、`JdbcOAuth2AuthorizationService`，独立 PostgreSQL 数据库 |
| 管理入口 | 环境配置审查与滚动发布；当前不提供管理员界面或开放注册 API |
| 验证状态 | 测试源码已补；未执行构建、测试或部署 |

## 授权来源

直接维护 `deploy/config/<env>/service-authorizations.json`。配置工具将文档放入版本化 ConfigMap 的 `AUTHSERVER_SERVICE_AUTHORIZATIONS` 环境值；文档只包含授权和 Secret 环境变量名，不包含密码。

```json
{
  "clients": [{
    "clientId": "acquire.default.payment",
    "tenantId": "__default__",
    "serviceId": "acquire",
    "audience": "payment",
    "permissions": ["pay:payment:read"],
    "secretEnv": "ACQUIRE_OAUTH2_CLIENT_SECRET",
    "enabled": true
  }]
}
```

应用启动在事务及数据库锁下同步授权到 Spring 标准注册表。相同密码保留哈希；移除的条目禁用并替换密码哈希；`enabled=false` 同样停止新令牌签发。配置是授权的唯一维护来源，直接改数据库可能被下次启动覆盖。多副本应使用同一版本配置，避免旧配置实例重启恢复旧授权。

每条调用关系使用独立 client ID，同一调用方目前复用一份应用 Secret。请求方只能申请注册范围内的 scope；省略 scope 时使用已注册范围。权限写入 `permissions`，调用方不能通过请求中的 tenant_id、audience、service_id 改变身份。令牌包含 `sub=service_id`、`principal_type=SERVICE`、`tenant_id`、单一 `aud`、`token_use=access`。

停用客户端不会立即撤销已签发 JWT；旧 JWT 持续到过期及资源端时钟容差结束。需要立即封禁时须另行设计撤销检查。数据库授权记录不会使离线验签自动查询授权状态。

## 租户与 schema

JWT 中保留逻辑租户身份，业务表无需恢复 tenant_id。AuthServer 的控制面数据存放在独立数据库中，其连接不跟随业务请求切换 schema。

当前环境仍只注册默认逻辑租户；各业务应用的默认租户绑定没有升级为动态多租户。后续需要补齐：验签后的租户解析、有效租户注册表、按应用解析 schema、事务前连接路由、连接归还恢复、异步/任务传播和缓存隔离。不能把此授权接入视为动态 schema 隔离已完成。

## 本地准备及发布

```bash
# 只写本地忽略文件；保留已有密钥和非空值，不构建、不访问 Kubernetes。
./scripts/authserver.sh prepare-dev
# 先由管理员执行 scripts/init-local-databases.sql，准备 loadup_authserver 数据库。
# 更新的 framework AuthServer SAS / kms-local / HTTP 组件须先由本地构建者安装。
APP_ENV=dev IMAGE_TAG=auth-001 ./scripts/up.sh authserver
APP_ENV=dev IMAGE_TAG=auth-002 ./scripts/up.sh all
```

第一步从现有开发数据库配置读取本地密码，并生成缺失的应用凭证及 PKCS12；不覆盖已有业务密钥。其他环境手动准备独立凭证和持久化 KeyStore。不要把 `secret.env`、`.p12`、令牌放入版本控制。

调用方配置 `SERVICE_AUTH_CLIENT_SECRET`，与 AuthServer 中该调用方的 Secret 一致。`SERVICE_AUTH_TOKEN_URI` 指向 token endpoint，issuer 是稳定身份标识，两者不要求相同地址。dev 显式允许集群内 HTTP；test/prod 默认要求 HTTPS，并配置受信任证书。

新部署默认使用 JWKS，不挂载静态 JWT。旧镜像不支持新取令牌方式，必须协调发布；旧 JWKS、旧 Secret、旧归档仅在确认所有消费者退出并完成验收后人工退役。当前没有修改运行集群。

AuthServer 使用内网 ClusterIP 和限制调用方的 NetworkPolicy，未向 Envoy Gateway 添加公开入口。外部商户/用户认证以及已有的商户令牌委托不在本应用的 client_credentials 流程内。

## 密钥与令牌轮换

KeyStore 文件 Secret 为 `authserver-files-<SECRET_VERSION>`，只读完整目录挂载到 AuthServer；密码由该应用环境 Secret 提供。文件内容参与 Secret 版本摘要。SAS 的保留公钥通过 `loadup.security.auth-server.signing.verification-keys` 配置，参见框架 AuthServer README。

先给所有副本分发新 alias 并发布新旧公钥，再切换签名 alias，保留旧公钥至旧令牌有效期、时钟容差与缓存窗口结束。ConfigMap 和 KeyStore 均在启动时读取，不承诺热刷新。调用方 Secret 更新同样通过新版本和滚动生效。

Spring 标准授权表采用 [官方 7.1 schema](https://github.com/spring-projects/spring-security/tree/7.1.x/oauth2/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization)，按 PostgreSQL 要求将 blob 改为 text、timestamp 改为 timestamptz。后续升级 Spring Security 须复查 schema；实际 PostgreSQL/JDBC、KMS、跨进程 JWKS 缓存和滚动轮换仍待验收。
