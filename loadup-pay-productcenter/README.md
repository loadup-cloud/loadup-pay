# Product Center

产品目录、条件、组合、销售方案、商户合约、条款解析以及支付准入和额度发布的权威服务。原 productcore 已合并到本应用。

目录发布、签约和合约状态变更在本地执行。暂停合约与新单准入继续使用同一数据库和合约锁。签约前通过独立服务凭证查询 merchantcenter 安全商户资料；不连接商户数据库。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前绑定签名中的 tenant_id。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份。令牌须同时包含当前后管及目标 center 的 audience；跨商户签约查询两域时应包含 merchantmng、productcenter、merchantcenter 所需受众。现有 AuthServer 仍仅提供 SERVICE 的 client_credentials；本次拆分没有新增操作员登录或商户 JWT 签发。

## 入口

- 内部管理：`/api/internal/v1/productcenter/management/**`。
- 交易准入：`/api/internal/v1/productcenter/admissions/authorize`、`/detail`。
- 公网网关不将请求路由到 center。

## 构建与发布

由开发者执行 `python3 scripts/build.py productcenter`；完成配置后使用 `python3 scripts/up.py productcenter`。配置目录为 `deploy/config/<env>/productcenter`，既有物理数据库名称与迁移版本保持不变。

本次仅修改源码、装配和部署清单；未运行 Maven 构建、测试或集群发布。
