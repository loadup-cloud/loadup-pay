# Pay OAuth2 Server 架构

单一可执行 JAR，不再拆分 bootstrap。应用只负责 Pay 身份/claims 策略、服务专用协议链和部署配置。
框架 oauth-server-core 提供 KMS 签发与标准 JDBC 客户端、授权及同意存储。

数据库是客户端配置的权威来源；应用启动不读取 catalogJson，不同步目录、不覆盖注册、不执行注册锁。
管理方通过 RegisteredClientRepository 保存标准 RegisteredClient，密码保存 BCrypt 散列。
client settings 的通用键为 loadup.oauth.enabled、loadup.oauth.audience；Pay 扩展为 pay.service.identity、pay.service.principal-type、pay.service.merchant-id。
已有数据通过应用自身 Flyway 迁移移除租户设置并迁移通用设置键，保留 client ID、密钥散列、scope 与有效期。

OAuthTokenCustomizer 每次签发重新读取数据库记录，检查 enabled、client_credentials 和授权 scope。主体支持 SERVICE、MERCHANT_APP、MERCHANT。
身份及 audience 来自当前服务端授权，不来自令牌请求参数。令牌不写 tenant_id，资源端不据此选择租户；Pay 内部存储路由由应用自己的 DatabaseTenant 配置决定。

服务专用协议链保持无用户登录、OIDC 或管理 HTTP 端点；其他路径只开放基础健康探针。
JWT 本地验签不查询授权数据库，停用客户端不会立即撤销已签发 JWT。原有 5 分钟令牌有效期需由客户端 TokenSettings 继续显式配置。
