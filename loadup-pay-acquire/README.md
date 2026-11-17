# Acquire 收单订单

管理订单、付款金额占用、收银台会话、订单费用和商户通知。付款与退款资金事实归 payment，准入归 productcenter。金额使用整数人民币分；存储、租户与认证基线见 [总体架构](../ARCHITECTURE.md)，事务与恢复规则见 [ARCHITECTURE](ARCHITECTURE.md)。

## 接入

启动模块为 `loadup-pay-acquire-bootstrap`，HTTP 默认 8080，`local` profile 为 8082。配置入口为 [application.yml](loadup-pay-acquire-bootstrap/src/main/resources/application.yml)。

| 配置 | 用途 |
|---|---|
| `ACQUIRE_JDBC_URL/USERNAME/PASSWORD` | 独立 PostgreSQL 数据库 |
| `JWT_ISSUER_URI` 及 JWKS/公钥配置 | 验证入站主体；audience 为 acquire |
| `SERVICE_AUTH_TOKEN_URI/CLIENT_SECRET` | OAuth2 出站服务凭证；注册及权限须与 oauth2-server 一致 |
| `ACQUIRE_CHECKOUT_PAGE_URL` | 与 cashier API 同源的公开 `/checkout` 地址 |
| `ACQUIRE_CHECKOUT_SECRET`、`ACQUIRE_CHECKOUT_ACTIVE_KEY_VERSION` | 会话 HMAC 密钥与版本；外部注入至少 32 个随机 UTF-8 字节，保留旧版本供重放 |
| `ACQUIRE_PAYMENT_RECOVERY_POLL_DELAY_SECONDS`、`ACQUIRE_ORDER_CLOSE_POLL_DELAY_SECONDS` | 付款恢复、关单续办间隔，默认 30 秒 |
| `ACQUIRE_TASK_*` | Task 批量、重试和租约参数；轮询使用 loadup.task.poll-interval |

HTTP 客户端按 productcenter/payment/realtime 命名，默认 `http://<app>:8080`，local profile 覆盖 localhost 端口。业务表、组件快照及任务使用本应用数据源；迁移只选择 `db/migration/pay/acquire`。部署流程及脚本限制见 [路线图](../ROADMAP.md) 和 [配置与发布](../deploy/CONFIGURATION.md)。

## API 与身份

AcquireActor 仅表达本地部署范围、稳定主体摘要和权限。委托 productcenter 的原 JWT 由 infrastructure 的 DelegatedCredentialResolver 逐请求从当前已认证上下文解析，不保存到 Actor，不缓存或回退为 SERVICE 凭证。主体、商户绑定、权限与幂等规则不变；本地缺身份/凭证仍拒绝，远端认证失败仍按依赖故障处理。实现和未运行的回归清单见[阶段 1A](../design/PHASE1A_IMPLEMENTATION.md)。

以下均为 POST JSON；业务响应使用 SuccessResponse/FailureResponse，分页使用 PageResponse。完整 Command/Query/DTO 以 client 契约为准。

| 路径 | 权限或用途 |
|---|---|
| `/api/acquire/orders/{create,detail,close,extend}` | `pay:acquire:create/read/close/extend` |
| `/api/acquire/checkout/create` | `pay:acquire:checkout`；创单并签发首次会话 |
| `/api/acquire/session/create` | `pay:acquire:session`；为可续付原订单签发会话 |
| `/api/acquire/session/{detail,revoke,revocations}` | `pay:acquire:session:read/revoke`；原签发主体管理 |
| `/api/acquire/payments/{start,refresh,recover}` | `pay:acquire:payment:start/refresh/recover` |
| `/api/acquire/payments/direct-pay` | create/read/payment:start/payment:recover 的组合权限 |
| `/api/acquire/payments/direct-pay/recover` | `pay:acquire:direct-pay:recover` 及 create/read/payment:recover |
| `/api/cashier/order/summary`、`/api/cashier/payments/{start,progress,recover}` | 对应订单会话 capability，不接受商户身份替代 |
| `/api/acquire/notifications/{detail,page}` | `pay:acquire:notification:read`，当前实际 merchant_id |
| `/api/acquire/notifications/{wake,wakes}` | `pay:acquire:notification:manage`，受信 OPERATOR 或配置的 SERVICE |
| `/api/acquire/operations/recovery/tasks/{detail,retry}` | `pay:acquire:recovery:read/retry`，配置的运维 SERVICE |

商户入口接受 MERCHANT_APP/MERCHANT/OPERATOR；MERCHANT 的 sub 必须等于 merchant_id。订单固定原调用主体范围，同商户其他应用/操作员不自动获得访问权。部署标识取自本地固定配置，不要求 JWT tenant_id。

创单准入使用原 JWT 委托 productcenter，再由其委托 merchantcenter；令牌须包含相关 audience 和权限。payment/realtime 出站使用独立 SERVICE 凭证，不转发商户令牌。内部 `/api/internal/v1/acquire/events/` 接收付款结果、退款费用汇总和退款通知，分别检查受信 payment 服务及最小权限。

## 托管收银台与通知

- 启用 `acquire-cashier` profile，并以 `ACQUIRE_CASHIER_H5_CHANNELS` 开通已验证的 H5 渠道；choices 与原冻结合约相交，默认目录为空。
- 页面与 cashier API 同源，不开放 CORS；须配置可信代理还原实际客户端 IP。JSAPI 动作桥接模块可复用，托管后端的渠道身份/OAuth 尚未接通。
- 商户异步通知按 `loadup.modules.acquire.merchant-notifications` 配置已审核 targets、signing-identities、KMS 版本及命名 HTTP 客户端；不能由订单提供任意 URL。签名、ACK、重试及人工唤醒约束见架构文档。

## 能力限制与待验收

不支持跨主体订单访问、超原窗口强行补发、跨设备会话恢复或无证据释放未知付款占用。首次输入准备在途、地址变化的恢复，目标引用发布前验证、紧急吊销/迁移、历史密钥保留及旧库任务补登记仍需完善。

待验收：PostgreSQL 迁移与并发回滚、付款/关单/延期/退款费用乱序、Task/Task 崩溃恢复、JWT 授权矩阵、浏览器/CSP/存储/代理、真实渠道与商户 ACK。持久化测试范围见 [测试说明](loadup-pay-acquire-infrastructure/src/test/README.md)，跨服务要求见 [最终验收](../deploy/ACCEPTANCE.md)。

阶段 3 已同步目标无关事实头与目标级交付边界；旧已冻结载荷/回执保留原样读取，身份、权限、原绑定、幂等和可靠交付规则不变。完整付款证据路径已替代 acquire 落账和额度确认/释放的同步回查；旧冻结载荷保持原查证，退款恢复和费用退款保留最新累计读取。当前契约与待运行验收见[财务证据记录](../design/PHASE3_FINANCIAL_EVIDENCE.md)。源码及回归源码已调整，未构建或测试；详见[阶段 3 实施记录](../design/PHASE3_IMPLEMENTATION.md)。
