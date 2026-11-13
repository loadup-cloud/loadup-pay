# Pay OAuth2 Server

单模块授权应用，提供 `/oauth2/token` 和公钥 JWKS。资源端通过 issuer/audience 验证令牌。

| 能力 | 实现或配置 |
| --- | --- |
| 客户端认证 | 标准 client_credentials / client_secret_basic |
| 签名 | Framework OAuth core，经 KMS 操作 RSA 私钥 |
| 持久化 | RegisteredClientRepository、OAuth2AuthorizationService、OAuth2AuthorizationConsentService |
| 授权来源 | 数据库，启动不导入或覆盖注册 |
| 业务身份 | SERVICE、MERCHANT_APP、MERCHANT |
| 租户 | OAuth 不携带或强制 tenant_id |

## 数据库和配置

开启 `loadup.security.auth-server.enabled` 与 `loadup.security.oauth.persistence.enabled`。
应用显式加载框架 `classpath:db/loadup-oauth` 与自身 `classpath:db/migration/pay/oauth2-server` 两处迁移。
已有注册通过一次数据迁移保留密钥散列、scope、有效期，并移除旧 tenant 设置；新数据库需先显式创建客户端。

客户端管理直接使用标准 `RegisteredClientRepository`。以下代码由受控的管理或初始化流程调用，**不要注册成应用启动回调**：

```java
var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
var client = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("acquire.default.payment")
        .clientSecret(encoder.encode(secret))
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
clients.save(client);
```

这里 `clients` 是注入的 `RegisteredClientRepository`，`secret` 由管理流程安全生成和交付，只持久化散列，禁止日志输出或查询回显。
更新时使用既有 ID 和 `RegisteredClient.from(existing)`；停用将 loadup.oauth.enabled 改为 false。
服务调用方的 client ID、secret、scope、audience 必须与数据库配置一致。开发脚本只准备调用方凭证和签名材料，不再向授权服务注入各调用方密钥或授权目录。
修改 scope、身份及 audience 后续签发会重新读取；已经签发的 JWT 直到过期仍可能有效。

应用仍使用 AUTHSERVER_JDBC_*、JWT_ISSUER_URI 和 AUTHSERVER_SIGNING_KEY 等部署变量；构建目标是 `loadup-pay-oauth2-server`。
设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。
