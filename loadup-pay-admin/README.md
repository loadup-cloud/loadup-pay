# Pay Admin

Admin BFF 提供操作员登录及 Pay 管理功能，并代理 OAuth 客户端管理。它通过 OAuth server 内部管理端点执行变更，不连接 OAuth 数据库，也不提供第二份客户端注册存储。

## OAuth 客户端 BFF

所有路由均为 POST：

| Admin 路径 | OAuth server 内部路径 | 权限 |
| --- | --- | --- |
| `/api/oauth/clients/{registrationId}/create` | `/api/internal/v1/oauth/clients/{registrationId}/create` | `oauth:client:create` |
| `/api/oauth/clients/{registrationId}/detail` | `/api/internal/v1/oauth/clients/{registrationId}/detail` | `oauth:client:read` |
| `/api/oauth/clients/{registrationId}/authorization` | `/api/internal/v1/oauth/clients/{registrationId}/authorization` | `oauth:client:authorize` |
| `/api/oauth/clients/{registrationId}/disable` | `/api/internal/v1/oauth/clients/{registrationId}/disable` | `oauth:client:disable` |
| `/api/oauth/clients/{registrationId}/expiry` | `/api/internal/v1/oauth/clients/{registrationId}/expiry` | `oauth:client:expire` |
| `/api/oauth/clients/{registrationId}/rotate` | `/api/internal/v1/oauth/clients/{registrationId}/rotate` | `oauth:client:rotate` |

权限定义由 `V20261114120000__oauth_client_permissions.sql` 注册，不自动向任何角色授权。

Each side requires an authenticated operator with `principal_type=OPERATOR` and its endpoint permission. Operator roles must be granted the relevant `oauth:client:*` permissions through the existing policy system. Admin forwards the incoming operator Bearer token unchanged. Admin overrides common `JWT_ISSUER_URI` with its own issuer and sets `JWT_AUDIENCE=admin`; OAuth server matches that issuer through `OAUTH_MANAGEMENT_ISSUER_URI`, uses audience `admin`, and reads the public JWKS at `GET /oauth2/jwks`. The JWKS endpoint is read-only. Other services continue using the common OAuth issuer.

Admin calls the internal server through the named `oauth-management` HttpTemplate client. Its default base URL is `http://oauth2-server:8080`, configurable with `OAUTH_MANAGEMENT_BASE_URL`. OAuth management requests have no automatic retries.

Create and rotate responses return a new plaintext client secret once and must use `Cache-Control: no-store`. Never put credentials in logs, exceptions, audit data, or response caches. If a create or rotate response is lost, its outcome is unknown: query detail, then explicitly rotate and securely deliver the replacement. Rotation replaces the only valid secret immediately; there is no overlap window. Previously issued JWTs remain valid through their expiry.

Admin records success, failure and denied MVC request traces on a best-effort basis. The OAuth server commits successful client mutations and the corresponding AuditFacade event atomically in its own database transaction. Audit data excludes credential values and secret-bearing request/response payloads.

Admin publishes only its own public key at `GET /oauth2/jwks` for internal management-token verification. No direct public OAuth server management endpoint or OAuth database access is part of this BFF.

## UPMS 装配（阶段 1C）

Admin 直接声明 UPMS infrastructure，Web/AuthServer 各自也显式选择默认持久化。AdminApplication 保持只扫描 io.github.loadup.admin，由 Boot 的 imports 自动发现 UPMS 的 persistence/app/web/authserver；不扩大包扫描，也不访问其他 Pay 领域表。新增 UpmsAssemblyTest 检查类路径发现和四层组合的 Bean 注册；使用模拟 JDBC/Mapper、关闭该夹具的协议端点，仅验证 UPMS 装配，不代表真实登录、安全链或完整 Admin 启动通过。已有 AuthServer 登录/权限测试、Admin Controller 测试仍保留。未构建或测试。
