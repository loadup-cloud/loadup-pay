# Pay OAuth2 Server

单模块授权应用，提供 `/oauth2/token` 和公钥 JWKS。资源端通过 issuer/audience 验证令牌。

| 能力 | 实现或配置 |
| --- | --- |
| 客户端认证 | 标准 client_credentials / client_secret_basic |
| 签名 | Framework OAuth core，经 KMS 操作 RSA 私钥 |
| 持久化 | RegisteredClientRepository、OAuth2AuthorizationService、OAuth2AuthorizationConsentService |
| 授权来源 | 数据库，启动不导入或覆盖注册 |
| 生命周期管理 | OAuthClientManager，按标准 RegisteredClient 授权字段创建或更新 |
| 业务身份 | SERVICE、MERCHANT_APP、MERCHANT |
| 租户 | OAuth 不携带或强制 tenant_id |

## 数据库和配置

开启 `loadup.security.auth-server.enabled` 与 `loadup.security.oauth.persistence.enabled`。
应用显式加载框架 `classpath:db/loadup-oauth` 与自身 `classpath:db/migration/pay/oauth2-server` 两处迁移。
已有注册通过一次数据迁移保留密钥散列、scope、有效期，并移除旧 tenant 设置；新数据库需先显式创建客户端。

客户端管理通过 `OAuthClientManager`，以标准 `RegisteredClient` 作为授权字段模板。以下代码由受控的管理进程或管理适配器调用，**不要注册成应用启动回调或未经保护的 HTTP API**：

```java
var client = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("acquire.default.payment")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("internal")
        .clientSettings(ClientSettings.builder()
                .setting("loadup.oauth.enabled", true)
                .setting("loadup.oauth.audience", "payment")
                .setting("pay.service.identity", "acquire")
                .setting("pay.service.principal-type", "SERVICE")
                .build())
        .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build())
        .build();
IssuedClientCredential issued = clientManager.create(client, expiresAt);
// Deliver issued.secret() once through an approved secret channel; never log or persist plaintext here.
```

创建模板不能带 client secret 或到期时间。服务端生成 32 随机字节并返回 URL-safe Base64 无填充的 43 字符 secret；数据库只存密码散列，返回对象的 `toString()` 已脱敏，明文仅随创建/轮换结果返回，后续查询不可获取。注册 ID 与 client ID 创建后固定。`clientManager.get(id)` 会返回不含 secret hash 的注册信息。授权更新用既有 ID 和 `RegisteredClient.from(existing)`；授权更新不会写回传入的凭证字段；停用调用 `clientManager.disable(id)`，设置到期调用 `setCredentialExpiry(id, expiresAt)`，立即轮换调用 `rotateCredential(id, expiresAt)`。停用后当前组件没有启用/重新启用操作。

OAuthClientPolicy 是应用扩展点；Pay 策略验证 Pay 专有身份与授权字段，不放进通用框架。管理写入在同一数据库事务内锁定对应客户端行，不存在启动全局锁。自动装配要求单一 DataSourceTransactionManager（含 JdbcTransactionManager）、同一 DataSource 上的 JDBC 操作及共享 Clock。标准 OAuth 表结构无需因管理 API 增加列。
服务调用方的 client ID、secret、scope、audience 必须与数据库配置一致。开发脚本只准备调用方凭证和签名材料，不再向授权服务注入各调用方密钥或授权目录。
禁用或凭证过期的客户端会从所有协议仓储查找中排除；每次签发还重新验证当前 enabled 状态、凭证版本、grant 和 scope。凭证轮换立即替换唯一有效 secret，没有宽限期，也没有 OpenBao KV 存取集成；KMS JWT 签名路径不变。修改授权、轮换或停用不能撤销已签发 JWT，它仍可验证至过期。

应用仍使用 AUTHSERVER_JDBC_*、JWT_ISSUER_URI 和 AUTHSERVER_SIGNING_KEY 等部署变量；构建目标是 `loadup-pay-oauth2-server`。

## Admin 客户端管理适配器

服务端管理端点只监听内部服务：`POST /api/internal/v1/oauth/clients/{registrationId}/{create|detail|authorization|disable|expiry|rotate}`。Admin BFF 调用此路径并原样转发操作员 `Authorization: Bearer ...`；OAuth server 使用独立 Resource Server 设置验证 Admin issuer、`admin` audience 和 Admin JWKS。部署变量为 `OAUTH_MANAGEMENT_ISSUER_URI`、`OAUTH_MANAGEMENT_AUDIENCE`、`OAUTH_MANAGEMENT_JWK_SET_URI`，不复用服务令牌的 `JWT_ISSUER_URI`。

每个接口都要求 `principal_type=OPERATOR` 并检查对应权限（操作员角色必须通过现有策略系统授予相应权限）：`oauth:client:create`、`oauth:client:read`、`oauth:client:authorize`、`oauth:client:disable`、`oauth:client:expire`、`oauth:client:rotate`。OAuth server 不发布公网 Gateway 路由，不提供绕过 Admin 的操作入口。OAuthClientManager 的成功数据变更和 AuditFacade 事件在同一数据库事务提交；审计记录不得包括客户端 secret 或其他凭证载荷。

管理操作不自动重试。创建或轮换的响应若丢失，调用方无法判断明文凭证是否已交付；先查详情，再显式轮换并安全交付新凭证。创建和轮换响应设置 `Cache-Control: no-store`。轮换立即替换单一凭证，没有宽限窗口；既有 JWT 仍有效至过期。Admin BFF 的成功、失败和拒绝请求诊断为尽力记录，不取代 OAuth 数据库中成功变更的原子审计事件。

Admin 只发布自己的公钥 JWKS，不访问 OAuth 数据库。JWKS 读取路径为 Admin `/oauth2/jwks`，仅供内部 OAuth server 验证管理 JWT。
设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。
