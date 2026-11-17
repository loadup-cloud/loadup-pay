# Pay OAuth2 Server 架构

单一可执行 JAR，不再拆分 bootstrap。应用只负责 Pay 身份/claims 策略、服务专用协议链和部署配置。
框架 oauth-server-core 提供 KMS 签发与标准 JDBC 客户端、授权及同意存储。

数据库是客户端配置的权威来源；应用启动不读取 catalogJson、不同步目录、不覆盖注册，也不执行启动期注册锁。
管理方通过 OAuthClientManager 管理标准 RegisteredClient 字段：创建、授权更新、停用、设置 secret 到期时间和立即轮换凭证。Pay 的 OAuthClientPolicy 校验 Pay 专有身份/授权字段；策略不进入通用框架。
创建输入不能包含 secret 或 secret expiry。管理器生成 32 字节随机值并返回 43 字符 URL-safe Base64 secret，数据库只存 PasswordEncoder 散列；IssuedClientCredential 的 toString 脱敏，应用必须只通过审批的秘密通道交付明文。注册 ID/client ID 不可修改。更新授权保留凭证，轮换立即替换单一 secret，禁用状态不会被轮换清除；没有旧新凭证并行窗口，也未集成 OpenBao KV。
client settings 的通用键为 loadup.oauth.enabled、loadup.oauth.audience；Pay 扩展为 pay.service.identity、pay.service.principal-type、pay.service.merchant-id。
已有数据通过应用自身 Flyway 迁移移除租户设置并迁移通用设置键，保留 client ID、密钥散列、scope 与有效期。

LifecycleRegisteredClientRepository 在所有标准协议查找中过滤 disabled/expired 注册。OAuthClientTokenValidator 在每次签发时重新读取当前记录，检查当前启用状态、凭证、grant 和授权 scope。管理写入通过事务锁定目标行；自动装配要求单一 DataSourceTransactionManager（含 JdbcTransactionManager）、同一 DataSource 的 JDBC 操作和框架共享 Clock。管理器不增加 schema 列。主体支持 SERVICE、MERCHANT_APP、MERCHANT。
身份及 audience 来自当前服务端授权，不来自令牌请求参数。令牌不写 tenant_id，资源端不据此选择租户；Pay 内部存储使用部署固定的 `loadup.database.schema`。

服务专用协议链保持无用户登录、OIDC 或管理 HTTP 端点；其他路径只开放基础健康探针。任何嵌入式管理入口或运维进程必须自行认证并授权调用者。
JWT 本地验签不查询授权数据库；停用、权限更新或凭证轮换不能撤销既有 JWT，令牌仍有效至过期。原有 5 分钟令牌有效期需由客户端 TokenSettings 继续显式配置。

## Admin 管理面

Admin 是唯一管理 BFF。公网前缀 `/api/oauth/clients/{registrationId}/{create|detail|authorization|disable|expiry|rotate}` 均为 POST；后端前缀为 OAuth server 内部 `/api/internal/v1/oauth/clients/...`。BFF 原样转发操作员 Bearer，双方分别校验 `principal_type=OPERATOR` 和各自授权所需的 `oauth:client:create/read/authorize/disable/expire/rotate`。

OAuth server 的管理 Resource Server 配置使用独立 `OAUTH_MANAGEMENT_ISSUER_URI`、`OAUTH_MANAGEMENT_AUDIENCE=admin` 和 Admin `/oauth2/jwks`，默认内部地址为 `http://admin:8080`。它不复用服务令牌 issuer；Admin 的 `JWT_ISSUER_URI`（在 Admin 配置中覆盖 common.env）必须与之匹配。OAuth server 不存在公网 Gateway 路由，Admin 通过命名 `oauth-management` HttpTemplate 访问 `OAUTH_MANAGEMENT_BASE_URL`。Admin 不持有 OAuth 数据库连接。

成功的数据变更与 OAuth 数据库中的 AuditFacade 事件在同一 TransactionTemplate 事务中完成。Admin MVC 侧成功、失败和拒绝轨迹为尽力记录。凭证 secret 不得出现在任一审计载荷。创建/轮换响应使用 `Cache-Control: no-store`，不自动重试；如果响应丢失，结果未知，通过详情查询判断状态并由操作员显式轮换恢复。单 secret 轮换无宽限窗口，已签发 JWT 仍有效到过期。

Admin 通过只读 GET `/oauth2/jwks` 发布本地签名公钥，以便 OAuth server 验证管理令牌。网络策略只允许 admin 与 oauth2-server 这两个方向的内部访问边，不增加 OAuth server 公网入口。

创建时尚无可锁定的行，因此在 PostgreSQL 事务内分别锁定 client ID 与注册 ID（按固定顺序的标识级 advisory lock），防止标准仓储 upsert 覆盖并发创建；唯一索引继续作为最终约束。修改已有记录使用行锁。这些锁只覆盖单次管理操作，不存在启动目录同步或全局注册锁。
