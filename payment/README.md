# Payment 业务实现

当前实现付款受理、权威查询、父订单执行封禁及单笔关闭的 COLA 业务切片。默认 bootstrap 仍为基础骨架；Maven 与 Spring 的 payment-business profile 共同启用业务装配。真实渠道提交、查询和退款仍待接入。

## 当前入口

以下均为内部 POST，只接受框架资源服务器已验证的指定 acquire SERVICE JWT；principal_type=SERVICE、sub=service_id、tenant_id 必须有效，service_id 必须等于配置的 acquire-service-id。商户凭证或自填 header 不能升级服务身份。

| 路径（前缀 /api/payment/executions） | Client API | 权限 | 响应数据 |
|----------------------------------|------------|------|----------|
| /accept | PaymentAcceptanceFacade.accept | pay:payment:accept | PaymentExecutionDTO |
| /detail | PaymentAcceptanceFacade.detail | pay:payment:read | PaymentExecutionDTO |
| /close | PaymentControlFacade.close | pay:payment:close | PaymentCloseDTO |
| /fence-parent | PaymentControlFacade.fenceParent | pay:payment:fence | ParentFenceDTO |
| /parent-fence-status | PaymentControlFacade.parentFenceStatus | pay:payment:fence:read | ParentFenceDTO |

业务成功使用 SuccessResponse，固定错误码使用 FailureResponse。完整 PaymentExecutionFacade 继承两个已实现切片，startAttempt 尚未注册实现或 HTTP 占位入口。

## 付款受理与查询

accept 只代表付款意图持久受理；首次返回 INIT/version0、空 attempt/action/terminalEvidenceId。当前没有渠道后台执行器，不能将受理视为扣款成功，也不能因此释放 acquire 占用。

付款固定绑定 orderId/allocationId/admissionId/payeeMerchantId/金额/币种；完整 AdmittedOrderTermsDTO 必须有效且 schemaVersion=1，本笔金额不超过原总额，选择的渠道/方式属于原冻结能力。原条款允许合约停止后的原单续付，不重新请求当前 product/customer。跨笔金额、次数和尾款占用仍由后续 acquire 编排负责，本地单笔校验不构造跨库原子性承诺。

可信 acquire 服务负责提供真实占用及原条款。compiledTermsHash 保留来源引用；本地 payloadHash 只校验持久副本，不能代替调用身份或跨库占用验证。preparedInputRef 冻结为原命令的一部分，后续执行仍需核验其归属、类型和有效期，不是直接扣款凭证。

受理按 parent_gate→payment_gate→payment_order 的顺序，在 READ_COMMITTED 事务中建立 OPEN 门禁和 INIT 付款。既有门禁必须 OPEN 且绑定相同订单/商户/服务；唯一键与瞬态冲突先回滚，再最多重试三次。同 tenant/paymentId 先比较原服务、摘要与完整解码命令，再返回原记录；initiatedAt 与 limitOperationId 不变。相同摘要不能掩盖改参，同 allocation 不能绑定第二笔付款。已关闭付款的 accept 重放只返回原 CLOSED 及证据，不重新打开门禁或扣款。

## 父门禁与收敛查询

fenceParent 持久写 FENCED，closeEpoch 取单调最大值；不同 requestKey 的低代次不会覆盖高代次，相同 key 改代次或其他参数报 IDEMPOTENCY_CONFLICT。没有任何付款时，也先建立父 tombstone，阻止迟到的新 paymentId。

parentFenceStatus 在父门禁行锁内查询。fenced 仅当当前持久代次覆盖请求代次；没有门禁、或查询高于当前代次，都不构造封禁证明。allChildrenConverged 还要求全部已有付款带可信终结证据；INIT/PROCESSING/UNKNOWN/CLOSING 或缺终结证据的终态均不算收敛。unknownCount 保守计入有 activeAttempt 的处理中/未知/关闭中付款，以及终态缺失证据的记录；不等同于仅统计 UNKNOWN 枚举。

父封禁不改写付款状态，不触发渠道关闭，也不将父封禁成功当作可以释放每笔金额的依据。需要 acquire 后续逐笔控制和可信结果编排；未来付款写者继续遵守父门禁→单笔门禁→付款→attempt 的锁顺序。

## 单笔关闭与证据

PaymentCloseCommand 当前字段为 paymentId/orderId/payeeMerchantId/requestKey/fenceEpoch/reasonCode，fenceEpoch 必须大于0。回执身份 tenant+PAYMENT_CLOSE+paymentId+requestKey；父封禁为 tenant+PARENT_FENCE+orderId+requestKey。服务端使用 UTF-8 长度分帧计算摘要，绑定租户、服务、类型、订单、付款、商户、key、代次和原因；不接受客户端提供控制摘要。

关闭按相同锁顺序执行，先持久封禁付款，再依据锁内事实处理：

- 尚未建立付款及门禁：插入单笔 FENCED tombstone 与不可变未收款证据回执，返回 payment=null；不伪造金额为0的付款。
- 已有门禁但缺付款：必须找到原持久未收款回执，并核对订单/商户/服务；缺少证据返回 SNAPSHOT_INVALID，不将记录缺失当作未扣款。
- INIT/version0/无 activeAttempt：从未准备过尝试，可在门禁下本地转 CLOSING→CLOSED，同事务保存终结证据与回执。
- PROCESSING/UNKNOWN，或其他仍未终结的初始化记录：只转 CLOSING；原 attempt、金额和首次受理时间保持，不出具未收款证据。
- SUCCEEDED 保留成功事实，noCollectionEvidenceId 为空；FAILED/CLOSED 只复用原终结证据，不重开或改写终态。

PaymentCloseDTO 含 paymentId/orderId/当前实际 fenceEpoch/fenced、可空当前 PaymentExecutionDTO 和 noCollectionEvidenceId。相同 key 重放原回执并读取当前付款，不换证据、不另开门禁；未知付款随后由渠道工作流收敛时，可返回新的当前终态视图。原回执存在而门禁缺失视为损坏，不静默补建。不同 key 对同一未创建付款继续复用原未收款证据。

门禁、付款变更和回执在同一数据库事务提交，CAS 验证 row_version；响应失败不让已提交的关闭重新变成可扣款。未收款证据只供后续可信内部消费，当前未接 acquire 释放占用、limitcenter 释放、Outbox 结果及渠道关闭扫描。

## 数据库与组件复用

V20261022020000 创建 payment_parent_gate/payment_gate/payment_order；V20261022030000 新增 payment_control_receipt，并要求 CLOSED/FAILED 持有 terminal_evidence_id。本服务实际4张业务表。回执含 kind/target/order/payee/service/requestKey/requestDigest/requestedEpoch/reasonCode/noCollectionEvidenceId，业务唯一键不包含 deleted。

DO 继承框架 BaseDO；Mapper/TableDef/Tables 由 LoadUp MyBatis-Flex 包装处理器生成，隔离处理器路径显式包含上游处理器。仓储只使用生成列并显式约束 tenant/deleted，所有表示转换复用 Spring MapStruct。门禁 rowVersion 和付款 terminalEvidenceId 已贯通 domain/DO/DTO；当前模型直接演进，无旧构造器兼容层。

完整受理命令保存为 intent_payload LONGTEXT，SHA-256 校验其副本；payload/输入引用在诊断 toString 中隐藏。不保存原 JWT、付款码、渠道 openId 或渠道凭证。目标草案的 attempt、敏感输入、退款、父级退款汇总等结构尚未迁移，不能直接执行 design/schema/payment.sql 代替实际脚本。

## 构建与运行

从 loadup-pay 根目录执行；-am 保证使用当前依赖模型而不是陈旧安装 JAR：

```bash
mvnd clean test-compile -pl payment/payment-bootstrap -am -Ppayment-business \
  -DskipTests -Dskip.spotless=true -Dskip.spotbugs=true

./payment/build.sh -Ppayment-business
```

运行设置 SPRING_PROFILES_ACTIVE=payment-business，JVM/TZ 为 Asia/Shanghai，paymentClock 默认 systemDefaultZone，业务时间 DATETIME(6)，数据库会话 +08:00。配置稳定数据库 ID sequence site，并注入：

| 环境变量 | 用途 |
|----------|------|
| PAYMENT_JDBC_URL / PAYMENT_JDBC_USERNAME / PAYMENT_JDBC_PASSWORD | 独立数据库与 Secret |
| PAYMENT_JWT_ISSUER_URI / PAYMENT_JWT_AUDIENCE / PAYMENT_JWT_JWK_SET_URI | JWT 验证 |
| PAYMENT_ACQUIRE_SERVICE_ID | 可信 acquire 服务 ID，默认 acquire |

业务 profile 开启 Flyway，启动会执行未应用迁移。现有 deploy/pay.yml 仍为基础应用配置，业务部署需要 Secret/ConfigMap/profile 覆盖层；本批没有执行数据库迁移、打包业务镜像或部署 Kubernetes。

## 验证记录

2026-10-22：IDEA MCP 已恢复，已执行关键 app/domain/client/adapter、Converter 与测试源码的错误检查。IDEA 对生成 Mapper/Tables 仍有索引未解析提示，未将其标为检查通过；为验证注解处理，执行 payment-business 的窄范围 mvnd clean test-compile，9个相关模块成功，MapStruct 与四个 DO 的生成类型编译通过。关闭 DTO 的嵌套 payment 映射已在编译反馈后显式声明并复验。

新增2类22个测试方法，累计本批业务切片6类46个方法已编译，未执行 JUnit。覆盖先关闭/父封禁后迟到受理、幂等改参、epoch 单调、INIT 本地关闭、未知保留CLOSING、成功终态保护、缺记录/缺门禁的证据边界、原证据重放、权限/事务拒绝及回执写入故障。

mock 端口只能证明编排和事务边界，不能替代真实 MySQL 锁、回滚和并发验收。真实 JWT/HTTP/MySQL 集成、渠道关闭收敛、金额/额度释放与后续结果事件仍待完成。
