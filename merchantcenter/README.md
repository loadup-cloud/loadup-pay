# Merchant Center

商户主档、经营状态、关系、逐项动作授权和安全商户摘要的权威服务。原 merchant 模块已合并到本应用；不再提供框架业务模块或浏览器管理入口。

商户档案、关系和授权使用同一个商户数据库。签约数据归 productcenter，本应用不复制或直接操作产品合约表。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前绑定签名中的 tenant_id。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份。令牌须同时包含当前后管及目标 center 的 audience；跨商户签约查询两域时应包含 merchantmng、productcenter、merchantcenter 所需受众。现有 AuthServer 仍仅提供 SERVICE 的 client_credentials；本次拆分没有新增操作员登录或商户 JWT 签发。

## 入口

- 内部管理：`/api/internal/v1/merchantcenter/management/**`。
- 商户摘要：`/api/internal/v1/merchantcenter/profiles/detail`；交易资格：`/api/internal/v1/merchantcenter/eligibility/authorize`。
- 公网网关不将请求路由到 center。

## 构建与发布

由开发者执行 `./scripts/build.sh merchantcenter`；完成配置后使用 `./scripts/up.sh merchantcenter`。配置目录为 `deploy/config/<env>/merchantcenter`，既有物理数据库名称与迁移版本保持不变。

本次仅修改源码、装配和部署清单；未运行 Maven 构建、测试或集群发布。
