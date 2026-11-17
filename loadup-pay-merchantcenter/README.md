# Merchant Center

商户主档、经营状态、关系、逐项动作授权和安全商户摘要的权威服务。原 merchant 模块已合并到本应用；不再提供框架业务模块或浏览器管理入口。

商户档案、关系和授权使用同一个商户数据库。签约数据归 productcenter，本应用不复制或直接操作产品合约表。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前使用本地固定部署配置确定部署标识与 schema。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份，由 admin BFF 原样转发操作员 JWT；目标 center 独立校验 issuer、audience 和权限。admin/UPMS 登录与机器令牌签发由不同应用负责。当前两个 center 对 Admin issuer/audience 的信任配置仍需联调验证，不能以 BFF Controller 存在认定管理链路可用。

## 入口

- 内部管理：`/api/internal/v1/merchantcenter/management/**`。
- 商户摘要：`/api/internal/v1/merchantcenter/profiles/detail`；交易资格：`/api/internal/v1/merchantcenter/eligibility/authorize`。
- 公网网关不将请求路由到 center。

## 构建与发布

由开发者执行 `python3 scripts/build.py merchantcenter`；完成配置后使用 `python3 scripts/up.py merchantcenter`。配置目录为 `deploy/config/<env>/merchantcenter`，既有物理数据库名称与迁移版本保持不变。

待验收：当前版本的数据库迁移、管理链路、服务鉴权与真实业务调用。
