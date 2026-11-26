# Product Center

产品目录、条件、组合、销售方案、商户合约、条款解析以及支付准入和额度发布的权威服务。原 productcore 已合并到本应用。

目录发布、签约和合约状态变更在本地执行。暂停合约与新单准入继续使用同一数据库和合约锁。签约前通过独立服务凭证查询 merchantcenter 安全商户资料；不连接商户数据库。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前使用本地固定部署配置确定部署标识与 schema。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份，由 admin BFF 原样转发操作员 JWT；目标 center 独立校验 issuer、audience 和权限。admin/UPMS 登录与机器令牌签发由不同应用负责。当前两个 center 对 Admin issuer/audience 的信任配置仍需联调验证，不能以 BFF Controller 存在认定管理链路可用。

## 入口

- 内部管理：`/api/internal/v1/productcenter/management/**`。
- 交易准入：`/api/internal/v1/productcenter/admissions/authorize`、`/detail`。
- 公网网关不将请求路由到 center。

## 构建与发布

由开发者执行 `python3 scripts/build.py productcenter`；完成配置后使用 `python3 scripts/up.py productcenter`。配置目录为 `deploy/config/<env>/productcenter`，既有物理数据库名称与迁移版本保持不变。

待验收：当前版本的数据库迁移、管理链路、服务鉴权与真实业务调用。

## 策略编译边界

阶段 2 已分离 domain CommercialPolicyParser、类型化策略片段、应用条款投影和额度命令编译。发布/准入总入口仍校验完整配置，额度片段不依赖完整收单编译器。公开 DTO、来源、快照与哈希协议保持原结构，未新增配置覆盖或通知执行策略。源码及回归源码已同步，未构建或测试；文件和验收清单见[阶段 2 记录](../design/PHASE2_IMPLEMENTATION.md)。

## 子模块入口

| 工件职责 | 接入 | 设计 |
| --- | --- | --- |
| 客户端契约 | [README](loadup-pay-productcenter-client/README.md) | [ARCHITECTURE](loadup-pay-productcenter-client/ARCHITECTURE.md) |
| 启动装配 | [README](loadup-pay-productcenter-bootstrap/README.md) | [ARCHITECTURE](loadup-pay-productcenter-bootstrap/ARCHITECTURE.md) |
| Web 适配 | [README](loadup-pay-productcenter-adapter/README.md) | [ARCHITECTURE](loadup-pay-productcenter-adapter/ARCHITECTURE.md) |
| 应用编排 | [README](loadup-pay-productcenter-app/README.md) | [ARCHITECTURE](loadup-pay-productcenter-app/ARCHITECTURE.md) |
| 领域模型 | [README](loadup-pay-productcenter-domain/README.md) | [ARCHITECTURE](loadup-pay-productcenter-domain/ARCHITECTURE.md) |
| 基础设施 | [README](loadup-pay-productcenter-infrastructure/README.md) | [ARCHITECTURE](loadup-pay-productcenter-infrastructure/ARCHITECTURE.md) |
