# 阶段 1A 业务身份与委托凭证分离

日期：2026-11-15。状态：源码、调用方、测试源码和文档已调整；未运行构建或测试，装配与运行验收待执行。范围仅 acquire → productcenter 的同步委托准入，本记录保留 1A 交付时点；后续[1B](PHASE1B_IMPLEMENTATION.md)已调整源码但未构建/测试，后续 1C 已同步源码（未构建/测试），后续[阶段 2](PHASE2_IMPLEMENTATION.md)已同步源码（未构建/测试）。

## 1 当前职责

| 位置 | 职责与改动 |
|---|---|
| [AcquireActor](../loadup-pay-acquire/loadup-pay-acquire-app/src/main/java/io/github/loadup/acquire/app/security/AcquireActor.java) | 只保留 tenantId、scopeHash、permissions；删除 productAccessToken，不保留兼容构造器 |
| [JwtAcquireActorProvider](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/main/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProvider.java) | 继续从已认证 JWT 校验主体类型、商户绑定、sub；tenant 仍来自 DatabaseTenant；原 SHA-256 主体摘要及权限来源不变 |
| [DelegatedCredentialResolver](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/security/DelegatedCredentialResolver.java) | 新增基础设施组件；每次先调用既有 AcquireActorProvider.current 校验业务身份，再从当前已认证 SecurityContext 的 Jwt credentials 取得原始 token |
| [OrderAdmissionHttpClient](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/main/java/io/github/loadup/acquire/infrastructure/client/OrderAdmissionHttpClient.java) | 检查无本地事务后解析当前凭证，显式传 Bearer；请求/响应转换和远端失败分类保持原顺序 |
| [infrastructure POM](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/pom.xml) | 直接声明实际使用的 spring-security-core 与 spring-security-oauth2-jose，版本沿现有 BOM 管理；不增加 adapter 依赖 |

依赖为 infrastructure → app 的 ActorProvider 业务身份接口及 Spring Security，不依赖 acquire-adapter 实现。AcquireActorProvider 与 OrderAdmissionClient 的业务方法签名不变；Spring 启动仍扫描 acquire 基包，新解析器按构造器注入。

解析器不保存 token 字段、不缓存、不解码字符串凭证、不从 principal 猜测 token、不持久化或输出凭证，也不回退到 ServiceAccessTokens。只有同步委托路径使用它；后台恢复继续采用原有、明确授权的 SERVICE 流程。

## 2 保持的行为边界

| 场景 | 当前结果 | 保护的原语义 |
|---|---|---|
| 本地事务已活动 | INVALID_STATE，先于凭证解析，不能联网 | 创单事务不跨网络 |
| 业务身份校验拒绝 | 原 ActorProvider 异常直接传播 | 主体/商户绑定/所有权校验不弱化，也不改为依赖失败 |
| 无认证、未认证、非 Jwt credentials、空凭证 | ACTION_FORBIDDEN，不能联网 | 缺少委托凭证不换机器身份 |
| 当前请求 token 轮换 | 每次读取当前原 token，不保存到 Actor | 传输凭证轮换不改变 scopeHash 或订单幂等身份 |
| 远端认证、传输、JSON/转换或响应信封失败 | DEPENDENCY_UNAVAILABLE，不附原异常 cause | 不将身份依赖错误持久化为合约拒绝，不泄露原始响应或凭证 |
| 合法 DENIED 准入决定 | 返回原准入 DTO 和原因 | 商业拒绝与依赖故障继续区分 |

入站签名、issuer、audience、token_use 与有效期仍由原 Resource Server 校验。目标 productcenter 继续独立验证原委托 token；解析器不新增本地 audience 判定，也不将原远端 audience 拒绝从 DEPENDENCY_UNAVAILABLE 改成其他错误。

本轮不改主体摘要算法、权限集合、商户订单号/动作键/请求摘要、HTTP 路径/请求体、准入回放、数据库事务、Task、执行门禁或恢复状态机。Actor 的内部诊断快照不再含原 token 字段；公开业务报文不因本次重构增加或迁移认证字段。

## 3 调用方与回归源码

应用层的八个用例测试及 AcquiringFinancialPersistenceIT 共 27 处旧构造器已迁移，只删除原第三个凭证参数并保留 tenant、scope、permissions；相关拒绝/幂等断言保留。

| 测试源码 | 同步或新增覆盖 |
|---|---|
| [JwtAcquireActorProviderTest](../loadup-pay-acquire/loadup-pay-acquire-adapter/src/test/java/io/github/loadup/acquire/adapter/security/JwtAcquireActorProviderTest.java) | 业务身份/权限、固定主体摘要、令牌轮换、诊断无 token、伪造租户、商户绑定、SERVICE 拒绝；补共享 JWT converter 经 eraseCredentials 后的身份读取 |
| [OrderAdmissionHttpClientTest](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/client/OrderAdmissionHttpClientTest.java) | 原 token 逐请求传播/轮换、原请求映射、商业 DENIED、远端认证归类、敏感异常不传播、缺凭证无网络、事务优先拒绝 |
| [DelegatedCredentialResolverTest](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/security/DelegatedCredentialResolverTest.java) | 原身份校验、当前上下文轮换/清理、未认证/非 Jwt/空凭证拒绝、原身份异常不重分类、无凭证诊断泄露 |
| app 的 CheckoutSessionManagement/CheckoutSession/DirectPaymentRecovery/DirectPayment/OrderCreation/OrderExtension/PaymentInitiation/PaymentRecovery 测试 | 迁移 Actor 构造器，保留原权限与所有权断言 |
| [AcquiringFinancialPersistenceIT](../loadup-pay-acquire/loadup-pay-acquire-infrastructure/src/test/java/io/github/loadup/acquire/infrastructure/repository/AcquiringFinancialPersistenceIT.java) | 迁移 fixture 中 Actor 构造器；不改变持久化断言 |

测试源码覆盖不代表通过。未运行 JUnit、MapStruct 生成、Spring 装配、安全链联调或数据库验证；本轮没有可用的 IDEA 检查工具，也不以临时手工编译或静态脚本替代它。

## 4 后续验收

G1-1/G1-2/G1-3 已完成本切片的源码调整与回归源码同步，运行结果待本地验证。G1-4～G1-6 由后续 1B/1C 承担，当前 1B 已同步回归源码，运行验收仍待执行；不能因此将整个阶段 1 标记为验收完成。

后续需要验证 acquire 的 app/adapter/infrastructure 及实际启动组合，特别是 Jwt credentials 生命周期、解析器唯一装配、原权限拒绝、令牌轮换以及原创单/准入幂等回放。按用户要求本轮不执行构建和测试；需要运行时仍遵守窄模块 mvnd clean 与匹配制品版本的纪律。
