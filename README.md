# LoadUp Pay

面向商户及子商户的外部渠道收单平台，采用 Java 25、Spring Boot 和 LoadUp BOM，按 COLA DDD 组织六个核心应用及独立后置对账应用。
支持目标为付款码（商户扫码枪输入）、JSAPI、H5，以及纯 API 的 DirectPay / 创单后付款；托管收银台是可选入口。
支持直连及服务商/子商户模型，默认 CNY。不建设消费者用户、余额、充值、提现或转账；Native 二维码和原生 APP 支付不在本次范围。

当前已有应用编排、数据库适配、HTTP/gRPC 接口、渠道插件和异步任务源码，已超出初始化/契约阶段。
最新修改仍按用户要求暂停验证：本轮没有运行 IDEA、构建、测试、数据库迁移、部署或真实金融调用。

## 当前能力矩阵

状态更新：2026-10-28。“源码已有”表示存在相应实现，不表示全部业务边界已完成；“未验收”指当前业务版本，没有用历史启动/编译记录替代。
自动化列只描述源码覆盖与本轮运行状态；各模块的具体契约、未覆盖范围和历史记录见对应说明。

| 能力                  | 当前源码及缺口                                                                                                                                                                                                                                                                                                                                        | 自动化验证状态                                                                                                                      | 当前业务环境验收 | 责任模块                                           |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|------------------|----------------------------------------------------|
| 商户/子商户资格与授权 | 资格、关系和逐项授权；主档复用框架商户能力                                                                                                                                                                                                                                                                                                            | 测试源码已有；本轮未运行                                                                                                            | 未验收           | [customer](customer/README.md)                     |
| 产品/合约/新单准入    | 持久准入、冻结条款、额度策略发布与 ACK；多 revision/共享策略及发布前渠道引用校验待补                                                                                                                                                                                                                                                                  | 测试源码已有；本轮未运行                                                                                                            | 未验收           | [product](product/README.md)                       |
| 订单与多笔付款        | Create/Checkout/DirectPay、金额占用、权威结果、关闭/延期与订单快照                                                                                                                                                                                                                                                                                    | 单测及 [A2/A3 MySQL IT 源码](acquire/acquire-infrastructure/src/test/README.md)；未运行                                             | 未验收           | [acquire](acquire/README.md)                       |
| 独立付款/attempt      | 安全输入、冻结路由、Y2 首提、Y3/claim/receipt、原号查询与关闭；关单 ACK 独立证据及 Y3 失败重放源码已补；部分输入竞态/确认保存前丢失仍待补                                                                                                                                                                                                             | 单测及 [Y2/Y3/恢复 IT 源码](payment/payment-infrastructure/src/test/README.md)；未运行                                              | 未验收           | [payment](payment/README.md)                       |
| 独立退款              | 固定 FIFO 计划/容量、串行单步执行、原号恢复、退款汇总及结果 Outbox；微信受理时间独立证据源码已补；多付款组合仍待补证据                                                                                                                                                                                                                                | 单测及 [退款 handler/回滚 IT 源码](payment/payment-infrastructure/src/test/README.md)；未运行                                       | 未验收           | [payment](payment/README.md)                       |
| 整单费用/退费         | 原订单总额计费、确认、累计退款基数及独立调整，不重算分笔费用                                                                                                                                                                                                                                                                                          | 测试源码已有；本轮未运行                                                                                                            | 未验收           | [acquire](acquire/README.md)                       |
| 商户日/周/月/终生限额 | 全桶预占、执行许可、确认/释放、按原规则桶退款恢复；扫描及共享策略边界继续收敛                                                                                                                                                                                                                                                                         | 测试源码已有；本轮未运行                                                                                                            | 未验收           | [limitcenter](limitcenter/README.md)               |
| cashier / 纯 API      | 会话、摘要、金额付款/原动作恢复、托管 H5 页面、会话撤销；托管 JSAPI OAuth/桥接、通用 SDK 待补                                                                                                                                                                                                                                                         | 后端/页面测试源码已有；本轮未运行                                                                                                   | 未验收           | [acquire](acquire/README.md)                       |
| 商户通知              | 付款/付清/关闭/退款独立通知、签名 ACK、任务窗口、查询与受控重试；目标迁移/撤销和轮换继续完善                                                                                                                                                                                                                                                          | 测试源码已有；本轮未运行                                                                                                            | 未验收           | [acquire](acquire/README.md)                       |
| 交易查询投影          | 四类完整快照、付款各已提交生命周期版本、receipt、版本幂等及商户分页查询；重建/检查点及新鲜度待补                                                                                                                                                                                                                                                      | 单测及 [四类投影 IT 源码](realtime/realtime-infrastructure/src/test/README.md)；未运行                                              | 未验收           | [realtime](realtime/README.md)                     |
| 渠道统一入口          | channel-api + Facade/Strategy/Registry；payment 不直接依赖厂商插件                                                                                                                                                                                                                                                                                    | [渠道边界门禁源码](channel/channel-core/src/test/java/io/github/loadup/pay/channel/core/ChannelModuleBoundaryTest.java)；本轮未运行 | 未验收           | [channel](channel/README.md)                       |
| 支付宝                | 付款码/JSAPI、直连 H5 动作、查询/关闭、普通退款及签名通知；服务商 H5 与部分丢失响应恢复待补                                                                                                                                                                                                                                                           | 协议/适配测试源码已有；本轮未运行                                                                                                   | 未验收           | [channel-alipay](channel/channel-alipay/README.md) |
| 微信                  | JSAPI/H5、V2 付款码首提/查证、查询/关闭、普通退款及 APIv3 支付/退款通知；付款码撤销补偿待补                                                                                                                                                                                                                                                           | 协议/适配测试源码已有；本轮未运行                                                                                                   | 未验收           | [channel-wechat](channel/channel-wechat/README.md) |
| 银联                  | 目标保留；具体产品/接入机构未定，尚无 Provider                                                                                                                                                                                                                                                                                                        | 无实现验收证据                                                                                                                      | 未验收           | channel 后续插件                                   |
| 风控                  | 默认放行、渠道前咨询与结果后独立反馈端口；外部系统协议后置                                                                                                                                                                                                                                                                                            | 端口/默认策略测试源码已有；本轮未运行                                                                                               | 未验收           | payment / 外部适配                                 |
| 对账                  | 七层应用、批次/封存输入、纯核对/多原因差错、独立存储/任务与 HTTP/gRPC 源码；微信 ALL 解析、手工 File/Transfer、payment 权威快照/导入及微信官方下载策略/只读 gRPC 源码已补；官方来源请求/原生任务/File/Transfer 接线源码已补，独立差错复核/审批源码已补，显式可信源端查询/观察留档源码已补，支付宝账单 grant 申请源码已补，下载/解析及受控金融处置待补 | 领域/编排/安全/映射/解析、源端快照及 File/Transfer 同库 MySQL IT 源码已有；未运行                                                   | 未验收           | [reconciliation](reconciliation/README.md)         |
| 任务与运营恢复        | 原生任务、业务游标/generation、FAILED 受控重试及只读详情；有界退避/抖动及单 JVM 渠道恢复并发源码已补；执行计时/在途量、首次 UNKNOWN 持久时间与只读存量源码已补（库存默认未启用）；分级查证列表/告警规则源码已补；真实 held/FAILED/Outbox、首提/集群配额、通知路由/工单与保留策略待 R3                                                                 | 原生持久化与付款/退款 handler IT 源码已有；未运行                                                                                   | 未验收           | acquire / payment / LoadUp                         |
| K8s / Headlamp        | 七应用构建/发布/就绪脚本、原生 Service/DNS 与受限 Headlamp 清单；业务配置需单独注入                                                                                                                                                                                                                                                                   | 历史记录保留；本轮未执行脚本                                                                                                        | 当前业务版未验收 | [scripts](scripts/README.md) / deploy              |

[review](design/review.md) 的 R1/R2 首批已有实现和测试源码，但 R1 的真实数据库证明尚未满足；R3–R8 继续推进。
门禁范围与尚未覆盖的约束见 [架构门禁](design/ARCHITECTURE-GATES.md)。当前未完成工作统一见 [ROADMAP](ROADMAP.md)。

## 设计与关键约束

入口：[架构](ARCHITECTURE.md)、[详细设计](DETAILED-DESIGN.md)、[数据库](design/DATABASE.md)、[Client API](design/CLIENT-API.md)、[合约 schema](design/CONTRACT-SCHEMA.md)、[已确认决策](DESIGN-DECISIONS.md)、[术语](GLOSSARY.md)。

- 订单固定总额，多笔成功金额恰好付清才 PAID；金额与次数须原子占用。PAID/CLOSED 不重开。
- 合约停止/到期禁止新订单，原有效 pending 订单按冻结条款继续完成。
- 退款独立，不改原订单状态、应收总额或累计已付；费用按订单总额统一计费。
- 每类事实归自己的服务；数据库锁内不做跨服务 IO。UNKNOWN、任务失败、超时或 NOT_FOUND 均不能代替未收款证据。
- Y2 提交权不可重建；恢复沿用原付款/attempt/账户/渠道请求号，退款沿用原退款号。Outbox 按目标独立投递。
- 本地边界采用 withTenantContext / readSnapshot / writeLocal，分别表达上下文、只读 RR 和可重试 RC
  写；见 [事务契约](design/TRANSACTION-BOUNDARIES.md)，源码调整未运行验收。
- payment 的共同 [付款/attempt 绑定](design/PAYMENT-BINDING.md) 与提交/观察/关闭前置条件分开表达；绑定值不能代替原 Y2
  票据、门禁或资金证据。当前为源码切片，未运行验收。
- 复用 LoadUp Money、ToStringAsJson/DiagnosticHidden、数据库、Outbox、DelayedTask、HTTP、KMS 和 gRPC 能力；不另建
  pay-common/pay-rpc 或注册中心。

## 构建、装配与 Kubernetes

七应用是 acquire、payment、customer、product、limitcenter、realtime、reconciliation。默认 bootstrap 提供启动/健康骨架；业务依赖由
Maven `*-business` profile 引入，运行时还需激活对应 Spring profile，并注入各服务独立数据库、JWT/服务身份、KMS/渠道等配置。
默认 `deploy/pay.yml` 仍是本地骨架清单，不能把 Pod 就绪视为业务装配或支付闭环完成。

仓库已有以下入口；此处仅说明用法，本轮未执行：

```bash
# 选择单应用及业务依赖构建
./payment/build.sh -Ppayment-business

# 单应用发布（依脚本和环境配置执行）
./payment/deploy.sh

# 仅检查 Deployment/EndpointSlice 技术就绪
./scripts/verify.sh payment
```

脚本使用 `mvnd clean`、显式 OrbStack context、本地镜像和固定 `loadup-pay` namespace，默认跳过测试。
其他集群的镜像仓库、Namespace、Secret、TLS 和业务 profile
由部署层提供；完整变量、构建与发布步骤见 [脚本说明](scripts/README.md)。

应用间协议为各服务拥有的 Protobuf gRPC，商户/运营、收银台及渠道通知保留 HTTP。
K8s 原生 Service → EndpointSlice → DNS 完成发现，gRPC 使用 `<service>-headless:50051`；没有 Nacos/Eureka。
地址、身份与调用契约见 [RPC 规范](design/RPC.md)。

## Headlamp 与时间

仓库保留 `deploy/headlamp.yml` 和下列本机入口；本轮未应用清单或生成 token：

```bash
./scripts/headlamp.sh install
./scripts/headlamp.sh open
./scripts/headlamp.sh token
```

默认通过本机 `http://127.0.0.1:14466` 访问；`headlamp/pay-operator` 受限于平台工作负载的查询和部署缩放，不授予 Secret
或集群管理员权限。
部署副本数为 0 表示下线，恢复副本数表示上线；重新应用清单会按其中副本数恢复。

应用、业务 Clock 和 JDBC 无偏移时间使用系统本地时区；不强制 UTC，不注入固定 TZ/user.timezone 或 JDBC SET time_zone。
数据库会话与应用环境保持相同本地时间语义；JWT epoch 与 Protobuf Timestamp 保持绝对时间。

## 证据与历史

当前源码、自动化运行和真实环境验收分开记录；新增 IT 使用显式 mysql-it profile，默认单测不会执行它们。
用户暂停验证期间，不运行构建/测试/DDL/部署，也不把测试文件存在当作测试通过。

历史初始化、编译及 OrbStack/Headlamp 启动记录见 [IMPLEMENTATION](IMPLEMENTATION.md)
。业务实施日志见 [BUSINESS-IMPLEMENTATION](BUSINESS-IMPLEMENTATION.md)。
整理前的根说明完整保存在 [历史快照](design/history/README-before-review-R8.md)，其中“当前/待实现”等词仅属于当时记录；旧
Ping RPC 已删除，旧启动记录不证明当前 gRPC、金融事务或真实渠道通过。
