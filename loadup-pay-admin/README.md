# Pay Admin

Admin 是操作员控制台和后端代理（BFF），负责 UPMS 登录、权限与审计，并为商户、产品、合约和 OAuth 客户端管理提供管理 API。它不直接访问 merchantcenter、productcenter 或 OAuth server 数据库，也不复制这些服务的业务数据或客户端注册。

## OAuth 客户端管理

下列路由均为 POST：

| Admin 路径 | OAuth server 内部路径 | 所需权限 |
| --- | --- | --- |
| `/api/oauth/clients/{registrationId}/create` | `/api/internal/v1/oauth/clients/{registrationId}/create` | `oauth:client:create` |
| `/api/oauth/clients/{registrationId}/detail` | `/api/internal/v1/oauth/clients/{registrationId}/detail` | `oauth:client:read` |
| `/api/oauth/clients/{registrationId}/authorization` | `/api/internal/v1/oauth/clients/{registrationId}/authorization` | `oauth:client:authorize` |
| `/api/oauth/clients/{registrationId}/disable` | `/api/internal/v1/oauth/clients/{registrationId}/disable` | `oauth:client:disable` |
| `/api/oauth/clients/{registrationId}/expiry` | `/api/internal/v1/oauth/clients/{registrationId}/expiry` | `oauth:client:expire` |
| `/api/oauth/clients/{registrationId}/rotate` | `/api/internal/v1/oauth/clients/{registrationId}/rotate` | `oauth:client:rotate` |

Admin 与 OAuth server 两端都要求已认证且 `principal_type=OPERATOR` 的操作员，并分别验证端点权限。权限定义由 `V20261114120000__oauth_client_permissions.sql` 注册，不会自动授予角色；管理员须通过既有策略系统配置相应的 `oauth:client:*` 权限。

Admin 将收到的操作员 Bearer token 原样转发，不改为机器凭证。Admin 使用自己的 `JWT_ISSUER_URI` 和 `JWT_AUDIENCE=admin`；OAuth server 通过 `OAUTH_MANAGEMENT_ISSUER_URI` 信任该 issuer、校验 audience `admin`，并从 Admin 的 `GET /oauth2/jwks` 读取公钥。JWKS 只公开公钥。其他业务服务继续使用公共 OAuth issuer。此管理客户端使用命名 `oauth-management` HTTP 客户端，默认地址为 `http://oauth2-server:8080`，可由 `OAUTH_MANAGEMENT_BASE_URL` 覆盖；请求不自动重试。

创建和轮换响应只返回一次新的明文 client secret，并设置 `Cache-Control: no-store`。不得将凭证写入日志、异常、审计记录或响应缓存。如果创建或轮换响应丢失，结果未知：先查询详情，再明确执行轮换并安全交付新密钥。轮换立即替换唯一有效 secret，不提供重叠窗口；既有 JWT 在各自到期前仍有效。

Admin 尽力记录成功、失败和拒绝的 MVC 请求轨迹。OAuth server 在自身数据库事务中原子提交成功的客户端变更及对应 AuditFacade 事件；审计内容排除凭证和含密钥的请求/响应载荷。此 BFF 不开放 OAuth server 的公开管理端点，也不访问其数据库。

## Merchant 与 Product 管理

Admin 还代理 merchantcenter 和 productcenter 的管理服务，原始操作员 token 随请求传递，由目标服务再次验证 issuer、audience、操作员身份与对应权限：

- `/api/merchantmng/**`：商户资料、关系和动作授权的管理接口。
- `/api/merchantmng/product/**`：目录详情及商户合约预览、签署、查询和状态管理。
- `/api/productmng/**`：目录版本、商户合约和额度策略发布管理。

Admin 不持有这些领域的权威数据；商户数据归 merchantcenter，产品目录、商业合约和额度策略发布归 productcenter。中心端 issuer/audience 信任配置与真实管理链路仍需联调验收，Controller 存在本身不代表调用已贯通。

## UPMS、签名密钥与启动配置

Admin 直接依赖 UPMS infrastructure、web 和 authserver 装配。`AdminApplication` 仅扫描 `io.github.loadup.admin`；UPMS 的 persistence、app、web、authserver 通过 Spring Boot 自动配置导入发现，不扩大包扫描或访问其他 Pay 领域表。UPMS 使用 Admin 自有数据库，迁移扫描范围包括 `db/migration/pay/admin`、`db/migration/framework/upms` 和 `db/migration/framework/audit`。

Admin 使用本地 KMS binder 装载 PKCS12 keystore：路径由 `ADMIN_KEYSTORE_LOCATION` 指定，默认 `file:/var/run/loadup/secrets/admin.p12`；密码从 `KMS_KEYSTORE_PASSWORD` 环境变量读取。JWT 签名密钥名称由 `ADMIN_SIGNING_KEY` 指定，默认 `admin-jwt`，版本为 1。密钥文件及密码应由部署 Secret 安全挂载和注入。应用自身 OAuth2 协议端点关闭；公开的 `GET /oauth2/jwks` 仅用于提供 Admin 的公钥。

主配置位于 `src/main/resources/application.yml`。运行需设置 `ADMIN_JDBC_URL`、`ADMIN_JDBC_USERNAME`、`ADMIN_JDBC_PASSWORD`、`DEPLOYMENT_CODE`、`DEPLOYMENT_DATABASE_SCHEMA`，以及按部署设置 `JWT_ISSUER_URI`、`JWT_AUDIENCE` 和 keystore 配置。默认 issuer 为 `http://admin:8080`，audience 为 `admin`；`/api/auth/login` 是安全配置中的公开登录入口。虚拟线程默认关闭，管理端点不会因本地配置自动开放。

## 装配与验收状态

`UpmsAssemblyTest` 检查 UPMS 自动配置候选发现及 persistence/app/web/authserver 的组合 Bean 注册；测试夹具使用模拟 JDBC/Mapper，并关闭该夹具的协议端点。因此它只证明装配，不证明真实登录、生产安全链、数据库迁移或完整 Admin 启动通过。现有 AuthServer 登录/权限测试和 Admin Controller 测试仍在仓库中。本次文档整理未运行构建或测试。

组件边界、数据库所有权及应用间 HTTP 规则见 [Pay 总体架构](../ARCHITECTURE.md)；Admin BFF、UPMS 和密钥职责见 [Admin 架构](ARCHITECTURE.md)。
