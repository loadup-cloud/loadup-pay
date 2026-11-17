# Realtime 交易投影

消费 acquire/payment 的订单、付款、退款和退款金额汇总快照，提供商户读模型。使用独立 PostgreSQL，不参与付款提交、额度或资金裁定，不同步回查交易源。版本与事务规则见 [ARCHITECTURE](ARCHITECTURE.md)。

## 接入

启动模块为 `loadup-pay-realtime-bootstrap`；HTTP 默认 8080，local profile 为 8086。配置见 [application.yml](loadup-pay-realtime-bootstrap/src/main/resources/application.yml)。

- 设置 `REALTIME_JDBC_URL/USERNAME/PASSWORD`、`JWT_ISSUER_URI` 和 JWKS/公钥，入站 audience 为 realtime。
- `REALTIME_ACQUIRE_SERVICE_ID` 默认 acquire，`REALTIME_PAYMENT_SERVICE_ID` 默认 payment；生产方通过 OAuth2 SERVICE 凭证调用，不转发商户令牌。
- 默认 schema 与业务本地时间遵循 [总体架构](../ARCHITECTURE.md)，不启用动态多租户路由。

| POST 路径 | 身份与权限 |
|---|---|
| `/api/internal/v1/realtime/projections/order` | acquire；`pay:realtime:projection:order` |
| `/api/internal/v1/realtime/projections/{payment,refund,refund-summary}` | payment；对应 `pay:realtime:projection:*` |
| `/api/realtime/trades/{detail,orders,payments,refunds}` | 有实际 merchant_id 的 JWT；`pay:realtime:trade:read` |

投影生产方必须匹配 SERVICE 的 sub/service_id；producer 标签不是身份。SERVICE 不自动获得商户查询权，无商户范围的 OPERATOR 或任意请求商户列表不获授权。完整类型见 [HTTP 契约](loadup-pay-realtime-client/ARCHITECTURE.md)。

## 查询限制与待验收

detail 只返回订单头和独立退款汇总，子付款/退款分页获取。缺订单返回 NOT_FOUND，缺退款汇总返回 null；读模型不提供可付余额。查询新鲜度、流重建/checkpoint、多商户操作员授权未实现。

待验收：乱序/重复/冲突、旧版本拒绝重开终态、投影与 receipt 原子回滚、真实身份/来源隔离，以及四类来源的端到端投递。测试入口见 [ProjectionPersistenceIT 说明](loadup-pay-realtime-service/src/test/README.md)。

阶段 3 已同步目标无关事实头与目标级交付边界；旧已冻结载荷/回执保留原样读取，身份、权限、原绑定、幂等和可靠交付规则不变。同步查证未因类型迁移而删除，完整证据与替代门禁仍开放。源码及回归源码已调整，未构建或测试；详见[阶段 3 实施记录](../design/PHASE3_IMPLEMENTATION.md)。
