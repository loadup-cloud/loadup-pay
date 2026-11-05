# Product Management

产品、条件、组合、销售方案版本管理，合约运营与额度准备操作。

本应用是无数据库的管理 HTTP 适配，不依赖 center 的 app、infrastructure 或仓储。通过框架 HttpTemplate 的命名连接池调用中心，使用固定内部路径，无自动重试或重定向。

公开前缀 `/api/productmng`；目录管理在 `/catalog/**`，合约运营在 `/merchant-contracts/**`，额度准备在 `/limit-publications/**`。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前绑定签名中的 tenant_id。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份。令牌须同时包含当前后管及目标 center 的 audience；跨商户签约查询两域时应包含 merchantmng、productcenter、merchantcenter 所需受众。现有 AuthServer 仍仅提供 SERVICE 的 client_credentials；本次拆分没有新增操作员登录或商户 JWT 签发。

## 运行

- 启动模块：`productmng/productmng-bootstrap`，Java 25 / Spring Boot 4.1。
- 配置：共用 `JWT_ISSUER_URI`、`JWT_JWK_SET_URI`；资源 audience 取 `spring.application.name`（productmng）。
- 下游地址：merchantcenter / productcenter 按应用名默认 `http://<app>:8080`，仅特殊地址单独配置。
- 配置目录：`deploy/config/<env>/productmng`；不要求 JDBC 凭证或 SERVICE_AUTH_CLIENT_SECRET。
- `python3 scripts/build.py productmng` 和 `python3 scripts/up.py productmng` 由开发者按需执行。

审核流程未接入。发布、签约、暂停、恢复、终止仍由 center 的业务动作校验状态与版本。签约后额度准备独立进行，不把签约成功当作交易就绪。

已补原凭证转发与权限拒绝的回归源码，未执行测试或部署。
