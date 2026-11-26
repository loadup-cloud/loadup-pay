# LimitCenter 商户额度

> 当前数据库基线为 PostgreSQL；运行验收状态不能由历史版本记录推断。

额度提供端与 payment 证据查证均使用 HTTP；服务仅暴露 8080，已移除 Dubbo binder 与 Nacos profile。见[总体架构](../ARCHITECTURE.md)。

提供规则激活、多桶预占、执行许可/校验、显式过期、确认/释放和成功退款恢复。事务与原桶恢复规则见 [架构](ARCHITECTURE.md)。


实现边界与内部流程见 [ARCHITECTURE](ARCHITECTURE.md)。

## 能力与入口

所有接口为 POST JSON，成功使用 SuccessResponse，业务失败使用 FailureResponse。认证失败由资源服务器安全链处理。

| `/api/internal/v1/limitcenter/operations` 路径 | Java Facade | 权限 |
| --- | --- | --- |
| `/activate` | LimitPolicyFacade.activate | `pay:limit:activate` |
| `/reserve` | LimitReservationFacade.reserve | `pay:limit:reserve` |
| `/detail` | LimitReservationFacade.detail | `pay:limit:read` |
| `/begin` | LimitExecutionFacade.beginExecution | `pay:limit:execute` |
| `/verify` | LimitExecutionFacade.verifyExecution | `pay:limit:verify` |
| `/expire` | LimitExecutionFacade.expire | `pay:limit:expire` |
| `/confirm` | LimitSettlementFacade.confirm | `pay:limit:confirm` |
| `/release` | LimitSettlementFacade.release | `pay:limit:release` |
| `/restore-refund` | LimitRefundFacade.restoreRefund | `pay:limit:refund:restore` |

现有业务 Facade 分别实现九个操作；MerchantLimitFacade 保留进程内组合视图。详情以
operationId 查询，限定原调用 service_id；令牌轮换不改变 service_id。

## 身份与策略来源

资源服务器验证 JWT 签名、issuer、audience、有效期及 token_use=access。adapter 还要求签名
claim：principal_type=SERVICE、service_id，以及 sub=service_id；service 标识最长64字符且不得首尾空白。部署标识与 schema 由本地固定配置决定，不要求 JWT tenant_id。权限取自框架认证结果。请求体与请求头不能指定可信调用身份；本地事务不再绑定租户上下文。

认证方只向受控 productcenter 发布流程授予激活权限，只向 payment 授予预占、执行、校验、过期和查询权限。这些内部服务接口不直接向商户开放。payment
必须从首次受理事实提供 operationId/paymentId、initiatedAt、金额及原准入 policyId/version/hash，不能接受商户或浏览器任填日期来选择额度窗口。

productcenter 已补初始规则的可靠发布/回执落库源码，见[product说明](../loadup-pay-productcenter/README.md)
。只有 productcenter 接收并校验本接口的真实ACK后才写本地ACTIVE；本服务不直接写 productcenter 数据库。两端尚未运行集成验收，不能据此宣称新单链路已贯通。

## 构建与运行

默认 bootstrap 包含 limitcenter 业务装配。下列命令供开发者执行：

```bash
# 在 loadup-pay 根目录，编译并执行所选模块及依赖的测试。
mvnd clean verify -pl loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap -am

# 沿用既有脚本打包 JAR 与本地镜像，脚本默认跳过测试。
python3 scripts/build.py limitcenter

java \
  -jar loadup-pay-limitcenter/loadup-pay-limitcenter-bootstrap/target/loadup-pay-limitcenter-bootstrap-0.0.1-SNAPSHOT.jar
```

| 环境变量                                              | 用途                                                                |
|-------------------------------------------------------|---------------------------------------------------------------------|
| LIMITCENTER_JDBC_URL                                  | 独立 PostgreSQL 业务库                            |
| LIMITCENTER_JDBC_USERNAME / LIMITCENTER_JDBC_PASSWORD | 外部 Secret 提供的凭证                                              |
| LIMITCENTER_JWT_ISSUER_URI                            | 服务令牌的可信 issuer                                               |
| —                                                     | 入站 audience 取 `spring.application.name`（limitcenter）           |
| LIMITCENTER_JWT_JWK_SET_URI                           | 签发方公钥地址                                                      |
| LIMITCENTER_RESERVE_SECONDS                           | 首次预占执行窗口，默认120秒                                         |
| LIMITCENTER_PERMIT_SECRET                             | v1许可HMAC密钥，至少32个随机UTF-8字节，外部Secret注入；无开发默认值 |
| LIMITCENTER_PERMIT_ACTIVE_KEY_ID                      | 默认v1；切换其他版本须同时提供对应keys配置                          |

密钥配置位于 loadup.modules.limitcenter.permit（active-key-id、keys映射）。多副本共享同版本密钥。轮换先在所有副本装载新旧keyId/密钥，再切
active-key-id；保留旧键直至所有在途执行与响应重放不再需要它。不得在同一keyId下换密钥，重放时摘要不匹配会拒绝。移除旧键不会使已存许可通过假签名重发；旧许可的数据库摘要校验可继续工作，但Begin重放会明确报
LIMIT_PERMIT_UNAVAILABLE。不要复用JWT/商户通知密钥。

业务启动启用 Flyway，只扫描本服务的 `db/migration/pay/limitcenter`；会执行未应用的迁移。数据库会话与 JVM/业务 Clock 使用同一系统本地时区，数据库使用 timestamp(6) without time zone。部署时还需按框架数据库约定稳定分配 ID sequence site。现有 deploy/pay.yml
没有这些配置，不直接用于业务上线；需外部覆盖层注入 Secret/ConfigMap 和 SPRING_PROFILES_ACTIVE。

## 验证边界

待验收：PostgreSQL 初始化、全桶并发/回滚、许可重放与轮换、原桶退款恢复，以及 productcenter/payment 的真实授权和 HTTP 链路。

本地启用 `local` profile 时，许可签名使用 `application-local.yml` 中固定的开发密钥（v1），可由 `LIMITCENTER_PERMIT_SECRET` 覆盖。覆盖值至少需要 32 个 UTF-8 字节；开发密钥仅用于本地，生产环境必须单独生成随机密钥。

阶段 3 已同步目标无关事实头与目标级交付边界；旧已冻结载荷/回执保留原样读取，身份、权限、原绑定、幂等和可靠交付规则不变。完整付款证据路径已替代 acquire 落账和额度确认/释放的同步回查；旧冻结载荷保持原查证，退款恢复和费用退款保留最新累计读取。当前契约与待运行验收见[财务证据记录](../design/PHASE3_FINANCIAL_EVIDENCE.md)。源码及回归源码已调整，未构建或测试；详见[阶段 3 实施记录](../design/PHASE3_IMPLEMENTATION.md)。

## 子模块入口

| 工件职责 | 接入 | 设计 |
| --- | --- | --- |
| 客户端契约 | [README](loadup-pay-limitcenter-client/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-client/ARCHITECTURE.md) |
| 启动装配 | [README](loadup-pay-limitcenter-bootstrap/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-bootstrap/ARCHITECTURE.md) |
| Web 适配 | [README](loadup-pay-limitcenter-adapter/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-adapter/ARCHITECTURE.md) |
| 应用编排 | [README](loadup-pay-limitcenter-app/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-app/ARCHITECTURE.md) |
| 领域模型 | [README](loadup-pay-limitcenter-domain/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-domain/ARCHITECTURE.md) |
| 基础设施 | [README](loadup-pay-limitcenter-infrastructure/README.md) | [ARCHITECTURE](loadup-pay-limitcenter-infrastructure/ARCHITECTURE.md) |
