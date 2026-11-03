# 应用间 OAuth2 客户端

共享 jar，使用 Spring Security 标准 `AuthorizedClientServiceOAuth2AuthorizedClientManager`，支持请求线程与后台任务。业务调用端只依赖 `ServiceAccessTokens`，不接触密码、缓存或 OAuth 响应解析。

| 能力 | 契约 |
| --- | --- |
| 调用入口 | `token(tenant, service, audience)`，只接受显式注册的身份组合 |
| 取令牌 | 标准 client_credentials + client_secret_basic，显式申请配置的 scope，不传入任意租户或 audience 参数 |
| 缓存 | 每个注册独立保存；到期前 30 秒重新获取，同一注册串行续取 |
| 超时 | 连接 2 秒、读取 5 秒；配置上限 30 秒，不跟随重定向 |
| 失败 | 隐藏响应正文/凭证；不能获取令牌时终止当前调用，无旧文件回退，无自动重试业务写操作 |
| 信任 | 接收方通过 JWKS 验证 JWT 并检查权限，客户端缓存不代替资源端验签 |

`pay.service-auth` 包含 token-uri、client-secret、registrations（每条配置显式 scopes）；默认只允许 HTTPS，开发 HTTP 必须显式开启 allow-insecure-http。配置见五个调用方 bootstrap 的 application.yml。

缓存键不使用当前登录用户；用户/商户的委托 token 仍由原有 Actor 路径处理。默认配置只对应一个逻辑租户；扩展注册不会自动实现业务数据库的 schema 路由。

已补缓存、分区、提前续取、授权失败及 HTTP 限制的测试源码；未运行测试。
