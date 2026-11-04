# Merchant Management

商户资料、关系和授权管理，以及以商户为中心的合约预览、签约与查询。

本应用是无数据库的管理 HTTP 适配，不依赖 center 的 app、infrastructure 或仓储。通过框架 HttpTemplate 的命名连接池调用中心，使用固定内部路径，无自动重试或重定向。

公开前缀 `/api/merchantmng`；商户 CRUD 在 `/merchants/**`，签约在 `/product/merchant-contracts/**`。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前绑定签名中的 tenant_id。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份。令牌须同时包含当前后管及目标 center 的 audience；跨商户签约查询两域时应包含 merchantmng、productcenter、merchantcenter 所需受众。现有 AuthServer 仍仅提供 SERVICE 的 client_credentials；本次拆分没有新增操作员登录或商户 JWT 签发。

## 运行

- 启动模块：`merchantmng/merchantmng-bootstrap`，Java 25 / Spring Boot 4.1。
- 配置：共用 `JWT_ISSUER_URI`、`JWT_JWK_SET_URI`；资源 audience 为 `merchantmng`。
- 下游地址：`MERCHANTCENTER_BASE_URL` / `PRODUCTCENTER_BASE_URL`，按实际使用配置；默认 K8s Service DNS。
- 配置目录：`deploy/config/<env>/merchantmng`；不要求 JDBC 凭证或 SERVICE_AUTH_CLIENT_SECRET。
- `python3 scripts/build.py merchantmng` 和 `python3 scripts/up.py merchantmng` 由开发者按需执行。

审核流程未接入。发布、签约、暂停、恢复、终止仍由 center 的业务动作校验状态与版本。签约后额度准备独立进行，不把签约成功当作交易就绪。

已补原凭证转发与权限拒绝的回归源码，未执行测试或部署。
