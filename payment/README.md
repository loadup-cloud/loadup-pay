# Payment 业务实现

当前已实现 COLA 分层下的付款受理与权威查询，完整付款执行链路继续按切片推进。默认 bootstrap 为基础骨架；业务装配由 Maven 与 Spring 的 payment-business profile 共同启用。

## 当前入口

| HTTP POST | Client API | 调用方与权限 | 返回 |
|-----------|------------|--------------|------|
| `/api/payment/executions/accept` | PaymentAcceptanceFacade.accept(PaymentExecuteCommand) | 指定 acquire 服务，pay:payment:accept | SuccessResponse<PaymentExecutionDTO> |
| `/api/payment/executions/detail` | PaymentAcceptanceFacade.detail(IdQuery) | 指定 acquire 服务，pay:payment:read | SuccessResponse<PaymentExecutionDTO> |

完整 PaymentExecutionFacade 继承受理切片；close/fenceParent/parentFenceStatus/startAttempt 尚未注册实现或 HTTP 占位入口。请求身份必须来自框架资源服务器已验证的 JWT：principal_type=SERVICE，sub=service_id，含 tenant_id，service_id 等于配置的 acquire-service-id。商户 JWT、原始 header、任意其他服务均不能借权限字符串升级身份。

accept 只代表付款意图已持久受理，首次返回 INIT、version=0，activeAttemptId/action/terminalEvidenceId 均为空。当前没有后台执行器，不会自动进入渠道提交；调用者不能据此认定已收款或释放 acquire 的金额占用。本批没有接入真实支付宝/微信/银联、额度预占、风控、敏感输入准备、付款结果事件或退款。acquire 的 startPayment/金额占用与该接口之间的实际调用仍待后续接入。

## 受理规则

付款与 orderId/allocationId/admissionId/payeeMerchantId/金额/币种固定绑定，原 AdmittedOrderTermsDTO 必须完整、schemaVersion=1，本笔金额不得超过原订单总额，渠道/方式必须属于原冻结能力。合约停止后的原订单续付使用该原条款，不重新请求当前 product/customer。付款金额下限、尾款、累计金额和次数占用由后续 acquire 付款编排负责；payment 的单笔校验不构造跨库金额原子性保证。

可信 acquire 服务负责提供真实占用凭证及原条款；原 compiledTermsHash 是来源引用，本地 payloadHash 只校验持久副本，不将 SHA-256 校验和当作跨服务身份认证。preparedInputRef 只是绑定原命令的引用，后续执行仍需核验归属、有效期与输入类型，不作为可直接扣款的凭证。

首次受理在 READ_COMMITTED 事务内按 parent_gate → payment_gate → payment_order 顺序执行。缺失门禁创建为 OPEN（领域先 INIT 后打开），已存在的非 OPEN 门禁拒绝新受理，并验证订单、商户及原服务绑定。父门禁还串行化同一订单的新付款。两类门禁与 INIT 付款同事务提交；唯一键/瞬态冲突先回滚再最多重试三次，不在已失败事务内继续读取。

同 tenant/paymentId 重放先核对原调用服务、requestDigest 及解码后的完整原命令，返回原付款视图；保留首次 initiatedAt 与 limitOperationId，不生成第二笔记录，不因门禁后续封禁而重新执行。输入变更即使保持原摘要也报 IDEMPOTENCY_CONFLICT。allocationId 在同租户内只能绑定一笔付款。首次 initiatedAt 取本服务本地 Clock 并精确到微秒，为后续额度归桶保留，不随重试改变。

## 持久化

`V20261022020000__payment_acceptance.sql` 创建 payment_parent_gate、payment_gate、payment_order 三表。DO 继承框架 BaseDO，Mapper/TableDef/Tables 通过 LoadUp MyBatis-Flex 包装处理器生成；处理器隔离路径显式包含上游 mybatis-flex-processor。仓储仅使用生成的类型与列常量，显式限定 tenant/deleted。

原完整命令保存在 intent_payload LONGTEXT，配套 SHA-256 payload_hash；服务读取时校验副本、参数验证与付款头绑定。payload 和输入引用不进入诊断 toString；不保存原 JWT、付款码、渠道 openId 或渠道凭证。数据库保留未来生命周期字段，但本批只写 INIT；后续尝试/封禁/关闭/成功需要各自事务、可信事实与事件实现，不能直接修改状态模拟支付。

当前新增受理支持字段 caller_service_id；parent/payment 门禁均保存商户及服务绑定。目标草案中 parent gate 的退款汇总字段及后续 attempt/退款表尚未迁移，不能直接执行 design/schema/payment.sql 替代实际脚本。前期以当前模型直接迭代，无旧模型兼容方案。

## 构建与运行

以下命令均从 loadup-pay 根目录执行。模块间改动使用 -am 编译依赖，避免 Maven 引用本地陈旧模型 JAR：

```bash
mvnd clean compile -pl payment/payment-bootstrap -am -Ppayment-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true

# 仅编译业务及测试源码，不执行测试。
mvnd clean test-compile -pl payment/payment-app,payment/payment-infrastructure,payment/payment-adapter -am \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true

./payment/build.sh -Ppayment-business
```

运行设置 SPRING_PROFILES_ACTIVE=payment-business，JVM/TZ 使用 Asia/Shanghai；配置 paymentClock 默认 systemDefaultZone，业务时间存 DATETIME(6)，数据库会话 +08:00。需要下列配置和数据库 ID sequence 的稳定 site：

| 环境变量 | 用途 |
|----------|------|
| PAYMENT_JDBC_URL / PAYMENT_JDBC_USERNAME / PAYMENT_JDBC_PASSWORD | payment 独立业务数据库与 Secret 凭证 |
| PAYMENT_JWT_ISSUER_URI / PAYMENT_JWT_AUDIENCE / PAYMENT_JWT_JWK_SET_URI | 框架资源服务器验证配置 |
| PAYMENT_ACQUIRE_SERVICE_ID | 允许调用本切片的 acquire 服务身份，默认 acquire |

业务 profile 开启 Flyway，启动会执行未应用迁移。现有 deploy/pay.yml 仍是基础应用配置，业务部署需要数据库/JWT Secret、ConfigMap 与 profile 覆盖层；本批没有打包业务镜像、执行数据库迁移或发布 Kubernetes 业务应用。

## 验证记录

2026-10-22：IDEA MCP 检查未在等待窗口内返回，按协作规则切换到 mvnd。上述窄范围 clean compile 成功（9个相关模块，含 business bootstrap），MapStruct 与 MyBatis-Flex 完成真实生成；clean test-compile 成功（8个相关模块），包含新增4类24个测试方法源码。没有执行 JUnit，不将测试源码编译等同于测试通过。

测试源码覆盖 INIT 受理/门禁写入顺序、原请求重放、同摘要改参、父/单笔 tombstone、商户绑定、占用凭证复用、权限/服务/JWT限制、冲突回滚重读、大整数/快照校验与 Converter 往返。mock 事务只检查边界，不证明真实 MySQL 行锁、并发或三表原子回滚。真实 JWT/HTTP/MySQL 集成验收、渠道执行与后续回调仍待完成。
