# Pay 未完成工作

更新：2026-10-27。当前能力只在 [根 README 矩阵](README.md#当前能力矩阵) 汇总；模块 README 维护契约，本文维护后续队列，下面带日期的记录是历史实施轨迹。
业务范围保持 [已确认决策](DESIGN-DECISIONS.md) 和完整业务目标；源码完成、自动化通过、真实环境验收分别记账。
用户仍暂停 IDEA/构建/测试/数据库/部署验证，本轮无新增通过证据。

## Review 队列

- [ ] R1：实际运行并评估 acquire A2/A3、payment Y2/Y3/退款/恢复、realtime 投影 MySQL IT；源码已补不代表原子性已证明。多付款 FIFO、实际扫描及跨进程网络故障仍需独立覆盖。
- [ ] R2：只读详情/加锁重试源码已分离；仍需真实锁竞争和过时详情重试验收。
- [ ] R3：有界退避/抖动源码已接付款/退款；仍需运行证据、首提/集群配额、真实 held/FAILED/Outbox 快照、通知路由/工单与证据保留策略；单 JVM 渠道恢复舱壁、handler 执行指标及首次 UNKNOWN 时间/库存源码已补。
- [ ] R4：付款/attempt 共同纯领域绑定及模式校验已接首提/恢复/Y3/回调/关单等源码；门禁与完整上下文源码也已补；所有入口组合及运行证明仍待完成。
- [ ] R6：acquire/payment 付款意图已改 owner-local 不可变格式与显式 MapStruct，剥离公开 Command/DTO JSON；编译、实际落库/重启恢复及现有开发数据处置证明仍待完成。
- [ ] R7：模块内规范 task key、领取元数据和双阶段 current/单调 successor 规则已共用，付款/退款锁序与状态机独立；共享协议与原生 handler IT 的运行证明仍待完成。
- [ ] R5：五个本地 helper 与调用方统一 withTenantContext / readSnapshot / writeLocal，保持原事务/错误语义；契约测试源码已补，编译/测试及渠道事务外调用的运行证明仍待完成。
- [ ] R8：根能力入口和首批源码/POM 门禁持续维护；门禁运行、字节码依赖证明及剩余基础设施入站适配边界仍待完成。
- [ ] R9：有实际数据量/执行计划后再确定深分页与导出 cursor，保留当前页码 API。

## 剩余业务切片

- [ ] 渠道：支付宝直连 H5 源码已补，服务商 H5 授权承载仍待定；微信付款码首提/查证源码已补，撤销补偿待补；银联产品与接入机构确定后补独立 Provider。所有直连/服务商路径需真实沙箱/渠道验收。
- [ ] 输入与动作：初次安全输入尚在途/地址变更竞态、缺失输入后的收敛、托管 JSAPI OAuth/可信渠道标识与浏览器桥接、有限密钥/平台证书/APIv3 轮换。
- [ ] 查证恢复：丢失 prepay/关闭 ACK 的持久操作证据与恢复，保持 UNKNOWN 占用，不以任务失败伪造资金终态。
- [ ] 合约/额度：多 revision/多产品共享策略、发布前渠道引用/开通校验、扫描及原桶恢复等未验收边界。
- [ ] 通知：目标生命周期、授权迁移/撤销、密钥轮换、超过通知窗口的受控人工处置及真实签名/ACK 投递。
- [ ] realtime：付款中间态生产源码已补；重建/检查点、乱序补齐、新鲜度及端到端独立投递验收待完成。
- [ ] 后置对账：七层应用、封存输入/批次/核对、存储/任务/接口及微信 ALL 格式解析源码已补；手工 File/Transfer 导入/封存源码已补，官方自动下载/来源证明、支付宝解析、权威导出、差错复核及受控查证待补，不直接改资金表。
- [ ] 外部风控：按后续真实协议实现 provider，保留默认放行咨询和独立结果反馈。
- [ ] 运行与交付：真实 JWT/TLS/gRPC、独立 schema/事务、多实例任务、Outbox/商户通知及三渠道业务链路；业务镜像、Secret 与环境发布配置未经验收。

## 历史实施记录

整理前的旧总览见 [历史快照](design/history/ROADMAP-overview-before-review-R8.md)。历史“继续实现”不得覆盖当前能力矩阵；历史编译或启动不代表当前修改通过。

## 2026-10-23 订单快照接线进度

- [x] 源码：acquire 创单/占用/结果/关闭/延期后，同事务冻结完整落库订单头、真实创建/更新时间与版本写 Outbox。
- [x] 源码：独立 handler、严格解码、受信 acquire SERVICE HTTP 客户端、原消息重试和类型化 ACK 校验；接入 acquire-business 配置。
- [x] 测试源码：事务/版本/租户边界、原始金额与时间、重放不新增版本、乱序 ACK 和凭证轮换。
- [ ] 上述源码编译、测试、真实 MySQL 回滚和服务链路验收；用户暂停验证，未执行。
- [ ] 付款中间态/退款快照、真实渠道、自动到期/恢复、商户通知、重建/检查点和后置对账，继续按 BUSINESS-IMPLEMENTATION.md
  完整范围推进。

## 2026-10-23 退款规划实现进度

- [x] 源码：商户退款创建/详情、签名 merchant_id 归属及权限、原退款号参数绑定重放。
- [x] 源码：成功付款/attempt/交易 claim/合约核对、原成功窗口、固定 FIFO 分配、三表计划与容量 CAS 同事务。
- [x] 测试源码：原渠道冻结、窗口边界、占用金额不可复用、关闭后独立退款、事务失败、商户隔离与存储转换。
- [ ] 上述编译/测试、迁移与真实数据库并发/回滚验收；用户暂停验证，未执行。
- [ ] 固定计划串行渠道提交/查证与结果、退款汇总、退费/额度恢复和通知；以及 BUSINESS-IMPLEMENTATION.md 保留的其他完整业务范围。

## 2026-10-23 退款结果与单步执行进度

- [x] 源码：单步固定串行执行、INIT 赢家持久提交边界、重复 worker 查询原号，事务外网络及本地 UNKNOWN。
- [x] 源码：强类型验证观察、回执/差错、全局真实退款流水认领与子项/申请累计/容量 CAS 同事务。
- [x] 测试源码：不能跳过提交、UNKNOWN 不回退、不重复提交/落账、跨租户认领冲突、终态矛盾及存储/摘要。
- [ ] 上述编译/测试、真实数据库回滚/并发和渠道验收；暂停验证，均未执行。
- [ ] 真实渠道退款 binder、自动串行驱动/查询恢复、订单退款汇总/事件、退费与限额恢复，继续完整业务目标。

## 2026-10-23 退款投影来源进度

- [x] 源码：独立订单退款金额聚合、规划占用/结果确认释放的同事务版本 CAS。
- [x] 源码：逐笔退款全版本和固定大小金额汇总完整 Outbox，独立重放/ACK/HTTP 操作。
- [x] 契约重构：去除无限历史 requests 列表和专用进度类，realtime/调用方/测试/文档同步。
- [x] 测试源码：汇总金额边界、UNKNOWN、原审计与渠道时间、事务失败、独立重试与 ACK 身份/版本。
- [ ] 上述编译/测试、迁移、真实消息及数据库并发/回滚验收；暂停验证，未执行。
- [ ] 真实渠道/自动调度、费用/额度恢复下游、商户通知、付款中间态、重建/检查点及对账，继续完整目标。

## 2026-10-23 退费持久化进度

- [x] 源码：退款基数同版本参数绑定、旧版本/新版本单调约束。
- [x] 源码：独立 fee_adjustment、原整单累计退费差额、费用确认时补算及费用 CAS 同事务。
- [x] 测试源码：早到退款、确认补算、重复/乱序、差额与插入失败传播。
- [x] 源码：gRPC 权威退款汇总查询、独立费用 Outbox 目标/投递、费用接收和同事务语义回执。
- [ ] 最新编译/测试、真实数据库原子回滚和竞争验收；用户暂停验证，未执行。

## 2026-10-23 费用退款跨服务接线进度

- [x] 源码：payment 原 acquire 专用退款汇总查询、parent→summary 锁内权威读取、缺失不伪造零。
- [x] 源码：金额汇总变化与独立费用 Outbox 原子入队；严格 fee codec、独立 handler、gRPC 客户端和精确语义 ACK。
- [x] 源码：acquire 锁外回查、早到基数、费用/调整/接收回执同事务；改参冲突与首次 ACK 重放。
- [x] 测试源码：身份与权限隔离、早到/乱序/重试、失败传播、目标隔离、int64 与客户端操作。
- [ ] 最新源码编译、测试、真实数据库竞争/回滚、JWT/gRPC/Outbox 验收及迁移/部署；用户暂停验证，均未执行。
- [ ] 真实渠道、自动驱动/恢复、额度退款恢复、商户通知、付款中间态投影、重建/检查点和对账，继续 BUSINESS-IMPLEMENTATION.md 完整范围。

## 2026-10-23 退款额度恢复接线进度

- [x] 源码：成功退款权威事实 gRPC、原付款/成功退款永久 claim 查证、当前付款累计退款与容量版本。
- [x] 源码：成功退款独立 LIMIT Outbox 目标、严格 codec/原命令重试/精确 ACK，原双向 SERVICE gRPC 凭证复用。
- [x] 源码：原冻结规则/原 buckets 高水位恢复、Confirm 前重试、NO_ACTION、bucket/item/业务回执同事务与首次 ACK 重放。
- [x] 测试源码：原月/终生/held/笔数一次、重复乱序、权威事实/权限、失败边界、RPC int64 与客户端。
- [ ] 上述源码编译/测试、真实数据库竞争和原子回滚、JWT/gRPC/Outbox 验收与迁移部署；用户暂停验证，未执行。
- [ ] 真实渠道、自动驱动/恢复、安全换 attempt、商户通知、付款中间态、重建/检查点和对账，继续完整目标。

## 2026-10-23 退款自动驱动与恢复接线进度

- [x] 源码：原退款计划/容量/Outbox 与恢复游标、首轮 DelayedTask 同事务登记，商户重放不重复排队。
- [x] 源码：受信任务/有效租约检查、原固定计划单步驱动、正常等待原子后继、旧代次/终结幂等、异常保留原代重试。
- [x] 源码：复用 DelayedTask/Scheduler local binder、business profile 配置、游标 DO/生成 Mapper/MapStruct/租户 CAS 与迁移。
- [x] 测试源码：计划登记失败、key/游标冲突、事务外执行、后继失败、旧代/终结、租约/租户及竞争边界。
- [ ] 最新源码编译/测试、组件及业务迁移、真实数据库回滚/竞争、多 Pod 租约/崩溃演练、实际渠道自动退款；暂停验证，未执行。
- [ ] 专用运维任务查询/重放、归档/失败积压监控；付款自动执行/查证、真实渠道、商户通知、查询重建和对账继续完整目标。

- [x] 渠道模块源码拆分：channel-api/core/alipay/wechat；Facade + 策略注册路由；payment 桥接只解析冻结绑定；支付宝协议基础与测试源码已迁入插件。
- [ ] 支付宝退款 Provider 已条件注册并补齐提交/查询协议映射；继续实现付款码/JSAPI/H5、支付查询/关闭、微信策略与回调，以及首次退款未到渠道的同请求安全恢复；本批源码及测试未编译/执行。

- [x] 支付宝退款提交/查询适配与 Provider 注册源码，查询核验单次退款身份/金额/成功时间；协议 GMT+8 与业务本地时间转换；可选运行 profile。
- [ ] 执行后续获准的渠道窄范围验证及真实渠道沙箱/资金链路验收；目前未执行。

- [x] 支付宝查单/未付款关闭协议源码与 Provider 接线；ChannelPaymentTarget、金额校验、关闭组合证据及竞态重查测试源码。
- [ ] payment 主链自动提交/查单/关闭驱动、H5 动作、安全换 attempt；关闭 ACK 丢失的持久日志/对账恢复。全部新增源码按用户要求未运行验证。

- [x] 独立提交输入/动作/结果契约、原订单标题/截止透传与 gRPC、原绑定输入解析工厂、支付宝付款码/JSAPI 提交协议源码及测试源码。
- [ ] Y2 前完整渠道预检、主链自动驱动、H5 页面/托管授权及回调；旧开发输入按新契约重新准备，未执行清理/迁移或验证。


- [x] 源码：payment_action 首份快照、Y3 观察/动作同事务接收、原服务授权详情/outcome/幂等返回，原 attempt/epoch/期限和门禁可见性约束。
- [x] 源码：ChannelActionCodec 归 channel-api/core，payment 存储仅依赖统一契约；严格白名单 JSON/摘要与有效期核验；测试源码覆盖冲突、过期、事务失败传播。
- [ ] 上述编译、测试、迁移、真实数据库原子回滚/多副本并发和运行装配，按用户要求暂停；自动提交生产者、H5、微信、托管授权和回调继续待办。


## 2026-10-23 付款自动执行与预检接线

- [x] 源码：原受理/游标/首轮 DelayedTask 同事务，首笔自动创建与 SERVICE startAttempt 共享逻辑；原生租约、代次 CAS 和正常后继任务。
- [x] 源码：统一 Provider 本地预检，原未绑定输入锁外解密，唯一 Y2 ticket 首次提交；原号查单/门禁关闭与 Y3 动作写入。
- [x] 源码：到期/封禁先关闭本地门禁，未 Y2 的本地证明、已 Y2 的未知保留占用；不重建提交权或切换账户。
- [ ] 编译、测试、生成代码、DDL、真实数据库原子回滚/竞争、任务装配/多 Pod 崩溃演练与实际渠道验收；按用户要求暂停。
- [ ] 关闭 ACK 丢失的确定证据、异常重试耗尽运维重放/监控和归档；安全换 attempt、H5/微信/银联、回调、通知、托管交互、重建和对账继续完整目标。


## 2026-10-23 JSAPI 动作查询恢复

- [x] 源码：query-only ChannelPaymentRecovery/Facade/Provider 路由及精确结果核验；不带输入或提交许可。
- [x] 源码：支付宝验签 WAIT 原号查询重建原 trade_no/固定到期 JSAPI 动作；不调用 CREATE/PAY，不依赖短期买家输入。
- [x] 源码：非关闭付款恢复任务→recordRecovery 的 Y3 观察/动作同事务，原首份动作/期限与门禁约束；核心、插件、桥接与编排测试源码同步。
- [ ] 编译、测试、真实数据库回滚/竞争与实际渠道验收，按用户要求暂停；UNKNOWN 状态的保守动作可见性、关闭 ACK 丢失、H5/微信/银联、回调、商户通知、托管交互、重建/对账等仍保留在完整目标中。


## 2026-10-24 支付宝原生回调

- [x] 源码：独立 ChannelNotificationFacade/Provider、可信 endpoint 租户/原 revision、原 attempt 查找索引与支付宝有界 form/RSA2 验签。
- [x] 源码：验签后原号查询、CALLBACK 归一观察与原 Y3 回执/金融事实/Outbox；非冲突提交后原生 ACK，失败无 success。
- [x] 源码：冻结 tenant/revision 的 HTTPS notify_url 目录只注入 JSAPI CREATE；公钥验证不依赖服务商 token 解密，公开路径和原生 Controller 接线。
- [x] 测试源码：合成 RSA 签名/篡改、重复/编码/账户/金额、无越权查询、ACK 顺序/失败、回执键、收包上限及 URL 配置边界。
- [ ] 编译、测试、DDL、公开路径/资源服务器装配、真实数据库通知/轮询竞态及真实渠道验收，按用户要求暂停。
- [ ] 微信/银联及退款外部通知、签名原文归档、关闭 ACK 丢失证据、H5、商户通知、托管授权、查询重建和对账等继续完整目标。


## 2026-10-24 渠道插件依赖边界

- [x] 源码：channel-api/core/alipay/wechat 独立模块，统一 Facade、Provider 策略与不可变注册表按原 channel 路由；payment 不持有厂商协议实现。
- [x] 源码：payment-infrastructure 仅依赖 channel-api，payment-bootstrap 显式装配 core 和厂商插件；账户接入模式与收款主体归属渠道身份多态，移除 payment 账户摘要中的厂商分支。
- [x] 文档及测试源码：渠道架构/依赖图/扩展步骤、同一收款主体跨应用/凭证别名的作用域约束及错误渠道类型拒绝。
- [x] 源码：移除 ChannelBindingService 的支付宝/微信硬编码白名单；账户契约校验身份，Facade/Provider 判断执行能力；补充账户类型与上游渠道不一致时禁止写入的测试源码。
- [ ] 编译、测试与启动装配未执行，按用户要求暂停。微信支付 Provider、支付宝 H5 与银联等真实渠道能力仍待实现，不能将模块拆分视为渠道业务完成。


## 2026-10-24 微信 JSAPI 接入源码

- [x] 源码：微信 APIv3 可信原版本凭证、最终 URI/字节 KMS 签名、固定平台公钥/响应头/原字节验签与严格 JSON。
- [x] 源码：直连/服务商 JSAPI，原 OpenID 应用作用域/子商户、金额/订单号/期限/可信通知地址预检；预支付仅受理与 RSA 唤起动作。
- [x] 源码：原号查询核验账户/金额/成功时间，REFUND 保留原付款事实；原查询与关闭 204 ACK 组合证明；query-only 恢复不重新下单。
- [x] 源码：条件 Provider、微信动作白名单快照/MapStruct 客户端投影、可选 payment-wechat 固定 operation 配置及聚焦测试源码。
- [ ] 编译、测试、框架 HTTP 新签名 API 依赖装配、真实数据库/渠道验收仍暂停。微信 JSON 回调/AES-GCM、退款、H5/付款码、丢失预支付/关单响应的持久证据恢复继续待办；其余完整业务目标不变。


## 2026-10-24 微信普通退款

- [x] 源码：独立退款 Adapter/Provider 接线，直连/服务商原交易与子商户、原 gross 总额/单笔退款额/固定退款号和 int64 分值。
- [x] 源码：申请仅受理，原号查询确认成功时间/微信退款流水；CLOSED 的失败证据与 ABNORMAL/不存在不释放容量。复用现有退款表/回执/容量/Outbox/恢复任务。
- [x] 配置与测试源码：固定退款 operation、严格金额/账户/身份、受理/终态、事务边界和退款标识路径编码；未运行验证。
- [ ] 原号安全重提（首次未达渠道）、异常退款 API/人工处置、微信退款通知与实际渠道验收；编译/测试/部署按用户要求暂停。整体剩余业务目标继续。


## 2026-10-24 微信支付回调

- [x] 源码：通知 input 多值头/字节约束、原绑定 identify、可信 endpoint→原 revision 桥接；JSON/form 同一路径按策略处理。
- [x] 源码：共享原字节 RSA 平台验签、固定平台标识/时间窗、原 APIv3 KMS 密文解封/AES-GCM、原收款账户/订单/金额核验与工作字节清零。
- [x] 源码：微信支付通知 Provider、原查询/Y3 金融结果接线、HTTP 200/500 原生 ACK；合成 RSA/AES-GCM/篡改/编码/多值头、租户/绑定与 ACK 测试源码。
- [ ] 编译、测试、公开路由/资源服务器装配、数据库与真实通知/轮询竞态验收均未执行，按用户要求暂停。微信退款通知、有界凭证轮换、H5/付款码、原号安全重提、其他完整业务目标继续。


## 2026-10-24 微信退款回调

- [x] 源码：独立类型化退款通知 hint/verified 契约与 Facade/Provider；微信复用验签/解密，原商户/子商户、事件、原交易/退款号与 gross 金额核验。
- [x] 源码：独立退款 JSON 入口、可信原绑定有界查已提交退款、原号查询与 CALLBACK 回执/退款结果事务/200或500原生 ACK；复用金融结果、容量与 Outbox。
- [x] 源码：按 tenant/revision 的退款专用通知地址与固定请求注入、callback lookup 索引 migration；相关协议/服务/入口/查询边界测试源码。
- [ ] 编译、测试、DDL、资源服务器装配、真实数据库竞态与真实渠道回调均未执行。原号安全重提、异常退款处置、可信密钥轮换、H5/付款码及其余完整业务目标继续。


## 2026-10-24 原退款号安全恢复

- [x] 源码：统一 recoverRefund 独立于 queryRefund，Facade 原身份/金额/来源校验与原绑定桥接；首次提交后由原任务驱动恢复，不增加新退款身份或 payment 厂商分支。
- [x] 源码：微信验签404/明确不存在、支付宝验签成功/原自然键/缺状态等受限条件下的原号原参数幂等重提；查询与提交摘要组合，受理仍非终态。
- [x] 测试源码：两渠道触发/不触发条件、纯查询无副作用、原请求/金额/账户、组合摘要、Facade/桥接及编排来源约束；未执行。
- [ ] 编译、测试、真实数据库/多 Pod 并发、原请求未达/响应丢失/任务崩溃与原号幂等验收仍暂停。其他未知结果处置、异常退款 API/运维、可信轮换、H5/付款码与全部剩余业务继续。

## 2026-10-24 微信 H5 与安全场景输入

- [x] 源码：H5_SCENE 真实终端 IP/WAP/IOS/ANDROID 类型契约、字面量校验、gRPC oneof、acquire 受理/原引用附加；KMS 指纹与单 attempt 输入绑定沿用现有流程，初始输入 CHECK 同步 H5。
- [x] 源码：微信直连/服务商固定 H5 operation、原金额/账户/截止/通知地址、验签原 h5_url、WECHAT_H5 白名单快照与客户端 REDIRECT；有效期取调用开始加五分钟和原截止较早值，无查询重下单。
- [x] 测试源码及文档：场景/加密准备/原绑定/幂等重放、gRPC 字段、两种模式、原地址/期限/错误与诊断隐藏；新增源码未运行。
- [ ] 编译、测试、初始 DDL/迁移校验和、HTTP 新签名 API 装配、原子 Y3/输入竞态、真实 H5 域名/Referer/浏览器及渠道验收，按用户要求暂停。
- [ ] H5 响应丢失/链接过期的持久证据与安全恢复、支付宝 H5、微信付款码、托管收银台/授权、商户通知、查询重建、对账及全部剩余业务继续；银联产品/机构待补资料。


## 2026-10-24 acquire 商户通知

- [x] 源码：PaymentSucceeded/OrderFullyPaid/OrderClosed 原结果事务登记、确定性事件身份、固定 UTF-8 body 与原合约策略；与首轮 native task 同事务，无锁内网络。
- [x] 源码：独立通知表/状态、rowVersion CAS、发送前后继登记/代次防重、合约退避/窗口/次数耗尽；显式 KMS 版本 RequestSigner/final URI 与字节签名、固定 HTTPS target/商户归属/路由指纹、严格2xx+eventId/SUCCESS ACK。
- [x] 源码及文档：acquire-business native delayedtask/local scheduler、可选签名/KMS/目录配置、实际 DDL/设计；生产/投递/退避/签名/ACK/隔离测试源码。
- [ ] 编译/MapStruct/数据库处理器、DDL、框架 HTTP 签名 API、KMS/调度启动、真实数据库提交/竞态与真实商户通知均未运行，按用户要求暂停。
- [ ] 通知状态查询/审计重放、目标授权迁移/紧急吊销、product 发布前引用验证、目录与历史密钥生命周期、消息归档和全部其他原目标继续；退款通知源码已接，验证仍待完成。银联产品/机构待资料，不影响其他实现推进。

## 2026-10-24 渠道模块边界收敛

- [x] 源码及测试源码：payment-app 直接声明 channel-api；新增 ChannelModuleBoundaryTest 约束 payment 业务模块仅引用 API、渠道实现无 payment/其他实现依赖、bootstrap 仅 runtime 装配；修正文档中上游 channel/method 与服务端 bindingCode 路由配置的边界。
- [x] 源码：渠道继续按 channel-api/channel-core/channel-alipay/channel-wechat 独立模块划分；统一 Facade + Strategy + Registry + Adapter，按上游选择和原冻结绑定路由，无 payment 厂商执行分支。
- [x] 源码：支付和通知 Facade 共用不可变 ChannelStrategyRegistry；重复注册拒绝、未知渠道拒绝、无默认策略；bootstrap 仅以 runtime 装配引擎和插件。
- [x] 源码：payment 回调 Controller 删除微信签名头名单，通用有界多值采集；协议头解释、验签和原生 ACK 归渠道插件，来路 headers 不参与可信身份路由。
- [x] 测试源码：注册快照/重复/缺失策略，以及跨厂商回调头透传、头数量/多值超限拒绝；模块文档已同步。
- [ ] 上述源码未运行 IDEA 检查、编译或测试，遵守用户暂停验证要求；原完整业务范围的其他待办继续保留。


## 2026-10-24 退款商户通知接线

- [x] 源码：子退款终态与整笔申请完成（含 PARTIAL_SUCCEEDED）独立 Outbox；严格源 codec/原消息重试/gRPC/精确登记 ACK。
- [x] 源码：acquire 专用 SERVICE 权限、原订单与 CONSUMED 付款绑定、原合约通知策略、固定正文/首轮任务/不可变接收回执同事务，无金融金额或生命周期修改。
- [x] 源码：复用既有签名与重试引擎，类型化订单/退款正文校验，商户 ACK 与内部登记分开；新 receipt 迁移/schema，移除 payment 重复投递表草案。
- [x] 测试源码及文档：幂等/权限/晚到付款/精确 ACK/原字节/部分成功、gRPC presence/int64；未执行。
- [ ] 编译/MapStruct/数据库处理器、DDL、真实数据库并发/回滚、SERVICE JWT、RPC、签名和实际商户通知验收仍暂停。
- [ ] 目标发布前引用验证/迁移/吊销、历史密钥生命周期，以及渠道/收银台/交易重建/风控/对账和全部原目标继续；通知查询/有界唤醒/审计源码已补，验证待完成。


## 2026-10-24 商户通知查询与运营恢复

- [x] 源码：商户 JWT 范围的详情/分页，operator/配置 SERVICE 的专门运维权限，框架分页和安全元数据 DTO。
- [x] 源码：显式观察版本/请求键/原因、不可变审计回执与当前 native wake 同事务；原任务缺失/失败/取消恢复，运行/待执行/未来退避不抢占或加速，原次数和窗口不重置。
- [x] 源码：新审计表与查询索引迁移、生成 Mapper/TableDef 约定、MapStruct/HTTP/JWT/客户端 API、审计分页；权限/隔离/幂等/预算/状态分支测试源码。
- [ ] 编译、生成代码/测试、DDL、真实 JWT/数据库回滚与并发、多 Pod 任务租约/崩溃恢复、实际通知验收均未执行，继续遵守用户暂停验证要求。
- [ ] 原窗口外人工补发授权、目标发布验证/迁移/吊销、密钥生命周期、其他渠道/收银台/查询重建/风控/对账和全部其他原目标继续。


## 2026-10-24 收银台付款后端

- [x] 源码：商户/收银台共用 A2、金额/次数/尾款与安全输入受理编排，session actionScope 幂等，锁内原订单/会话/期限校验；内部结果归属替换假商户身份。
- [x] 源码：CashierFacade start/progress、专用 capability HTTP 白名单、本会话付款隔离、安全 DTO、server H5 场景与显式部署目录/原合约能力交集。
- [x] 源码/测试源码：初期 scope 更新、原客户端/调用方与 MapStruct 同步；权限/幂等/金额流程复用、H5 输入及会话付款后台结果收敛。
- [ ] 编译/MapStruct、测试、真实数据库并发/回滚与会话竞态、可信代理客户端地址、渠道域名及实际支付均未验证，遵守用户暂停要求。
- [ ] JSAPI OAuth/页面交互与浏览器验收；会话撤销源码已补但未验证；H5 表单/动作/轮询源码已补但未验证，其余渠道/通知/交易重建/风控/对账和全部原完整目标继续。


## 2026-10-24 托管 H5 页面

- [x] 源码：手动金额/尾款/笔数与方式、固定动作元数据持久化/原键重试、原支付链接、进度收敛与下一笔付款，有界轮询/隐藏暂停。
- [x] 源码：BigInt/精确 int64 JSON、原操作恢复不含 token/URL、必需 accepted 判别、HTTPS 原地址与期限、静态资源 GET 独立公开及 CSP/文本/Referrer 规则。
- [x] 测试源码：纯 Node 核心精度/政策/幂等恢复/存储失败/受理标记/地址与期限，Java 安全 DTO/规则摘要同步；未执行。
- [ ] Node/Java/IDE、真实浏览器、跨窗口/旧动作过期与丢失响应、域名/代理和实际渠道付款验收均暂停。
- [ ] JSAPI OAuth/交互与进一步恢复；会话撤销源码已补但未验证；其他渠道/通知/交易重建/风控/对账及所有原完整目标继续。


## 2026-10-24 收银台链接撤销

- [x] 源码：原签发者商户 detail/revoke/revocations API、独立权限与租户/原主体隔离；元数据不返回 token/摘要/nonce/密钥或链接。
- [x] 源码：永久 revoked_at、精确版本 CAS、不可变请求回执与分页；订单→会话锁后重查回执，首次标记与审计同事务；业务终态/金融金额不改变。
- [x] 源码：认证/页面读取/新预占拒绝撤销会话，已提交 A2 意图继续履约；原签发键不重新输出已撤销链接，另签需新键及可付款订单。
- [x] 测试源码及文档：业务终态独立撤销、权限/原主体、同键改参/版本/等锁重放、审计隔离、失败传播、认证/预占与旧意图/签发重放门禁；迁移和全量表草案已同步。
- [ ] 未运行 IDEA/编译/MapStruct/测试/DDL/部署；真实 MySQL 原子回滚、唯一键、撤销/A2 并发和 JWT/HTTP/浏览器集成仍待验证。
- [ ] JSAPI OAuth/交互、丢失响应/过期动作进一步恢复、其他渠道/通知生命周期/交易重建/风控/对账及全部原完整目标继续。


## 2026-10-24 收银台原 actionKey 恢复

- [x] 源码：capability recover Command/DTO/Facade/HTTP；订单→会话锁内查本会话原预占，核对 owner/金额/choice/原准入；明确 NOT_OBSERVED/INPUT_REQUIRED/RESOLVED。
- [x] 源码：已绑定输入的原固定命令幂等受理与 A3 结果收敛；不准备新输入、不另做预占/选路、不换金融身份；非 HELD 不再次受理。
- [x] 源码及测试源码：页面缺 paymentId 时先按原 actionKey 恢复，再按原金额重试或查询已知付款；三态/原绑定/改参/跨会话/丢失回复/已释放意图测试源码与文档已补。
- [ ] 远端 input 回复/本地引用绑定丢失的恢复源码已补；未受理输入到期的有证据关闭源码已补，继续首次准备并发时客户端地址变动的恢复；会话到期后的商户核对与原动作期限恢复不能靠换键解决。
- [ ] 未运行 IDEA/Java/Node/浏览器/数据库/真实 RPC 或渠道验证；JSAPI OAuth/交互、其余渠道/通知生命周期/交易重建/风控/对账与全部原目标继续。


## 2026-10-24 安全输入原引用恢复

- [x] 源码：PaymentInputLookupQuery/DTO/Facade 与 SERVICE input:read 权限，完整原意图绑定；只读原引用/摘要/期限，缺失显式 false、过期/BOUND 不续期，无解密或 KMS。
- [x] 源码：payment 所有权的 InputLookup proto、严格 presence/枚举/金额/Timestamp MapStruct、operation/服务绑定/consumer；无内部 HTTP 或异常伪装缺失。
- [x] 源码：acquire 缺引用恢复先查原输入，再订单→session 锁内一次性补绑，同引用并发重放、不同引用拒绝；原金额/身份/场景/期限不变，再按原命令受理并收敛。
- [x] 测试源码及文档：原范围/权限/到期/绑定/缺失/无密码操作、RPC 精确金额/presence、原准备回复丢失/不另做预占与异常无回退；修正相关测试输入期限使用公开 DTO 的 OffsetDateTime 类型。
- [ ] 未运行任何编译、测试、数据库、浏览器或 JWT/RPC/真实渠道验证；acquire 挂载 SERVICE token 的 input:read 权限须由发行方配置。
- [ ] 未受理输入到期的有证据关闭源码已补；首次准备仍在途/地址变化竞态、JSAPI OAuth、其他渠道/通知/交易重建/风控/对账和全部原目标继续。


## 2026-10-26 原输入到期但未受理的收敛

- [x] 源码：InputExpire command/DTO/三态、专用 SERVICE 权限；完整原范围与 parent→child/payment/input 锁序串行化，已受理优先，不因输入过期取消在途交易。
- [x] 源码：仅未受理且到期时复用单笔 close 门禁/固定请求回执，父订单继续开放；无证明不释放、孤立数据拒绝、回复丢失精确重放，无 KMS/渠道或新表。
- [x] 源码：payment RPC/MapStruct/接线/consumer 与 acquire 原意图恢复；精确证明复用 A3 释放原占用，已付金额/其他预占/业务状态不变，不虚构 payment 或收费。
- [x] 测试源码及文档：条件三态/已受理各状态不变/迟到受理门禁/回执重放、scope/权限、RPC presence/金额/互斥证明、原占用与其他金额隔离、A3 重复/非法证明拒绝及页面结果接线。
- [ ] 全部 IDEA/编译/测试/数据库/浏览器/JWT/RPC/真实渠道验证未运行；挂载 acquire SERVICE token 需发行方配置 input:expire 权限。
- [ ] 首次输入准备仍在途/地址变化、DirectPay 回复丢失后的整体恢复、JSAPI OAuth/交互、其他渠道、通知生命周期、交易重建、风控/对账和全部原目标继续。


## 2026-10-26 商户纯 API 原付款恢复

- [x] 源码：PaymentRecoveryFacade/Command/DTO、merchant JWT 专门权限与 POST API；原 owner/tenant/merchant actionScope，不跨应用或 checkout 动作，三态及 accepted null/true/false 明确区分。
- [x] 源码：共用原输入查回/到期门禁与 A3；无新订单/输入/预占/金融身份；原 NONE/H5 摘要精确确认后复用原无输入命令，不把缺失安全输入当 NONE。
- [x] 源码及测试源码：共享 PaymentRecoveryState 取代 cashier 专用类型；权限/原归属/禁止创建/三态/证据收敛/JSON 未知判别/无输入恢复及非法引用拒绝，设计文档同步。
- [ ] 未运行 IDEA/Java/MapStruct/测试/HTTP/JWT/数据库或实际 RPC/渠道验收；商户令牌需明确授予 recover 权限。
- [ ] DirectPay 整体回复恢复源码已补；首次准备并发/地址变化、JSAPI OAuth/交互、其他渠道/通知/交易重建/风控/对账及全部原目标继续。


## 2026-10-26 DirectPay 创建与整体回复恢复

- [x] 源码：DIRECT_PAY 独立 A0、三入口唯一/模式约束、首笔 actionKey/金额/choice/输入种类固定摘要及非敏感元数据编码；raw code/身份/IP 不进入创建库。
- [x] 源码：原 DirectPay 先恢复再补尚未提交步骤，返回 PaymentRecoveryDTO/A3 当前订单；新增整体恢复 Facade/HTTP/专门权限，不需原系统 orderId 或 raw input。
- [x] 源码：仅续办入口在 A0 事务强制原行存在，不重新分配 ID；INIT 原准入、COMPLETED 不再准入、稳定拒绝/到期结果与首笔三态保持分离，恢复不新建 A2。
- [x] 测试源码与文档：首笔/入口冲突、丢失准入回复/原 ID、缺 A0 不创建、摘要字段与精确金额、原付款恢复/不重传输入、权限/终态/异常不伪造；开发期 CHECK 与全量表草案同步，无兼容分支。
- [ ] 未运行任何 IDEA/编译/测试/数据库/JWT/HTTP/RPC/渠道验收；商户令牌需按接口权限配置；没有执行 DDL、部署或签发令牌。
- [ ] 首次准备并发/地址变动、JSAPI OAuth/交互、其他渠道、通知生命周期、交易重建、风控/对账及全部原完整目标继续。


## 2026-10-26 渠道插件边界约束

- [x] 保持已有 channel-api/core/alipay/wechat 独立模块与 Facade + Strategy + Registry + Adapter；上游 channel/method 在合约允许范围内选择并冻结绑定，payment 只编排业务与持久化。
- [x] 补充测试源码：payment 生产代码禁止引用渠道实现包，渠道生产代码禁止反向引用业务内部类型；保留 Maven 依赖与 runtime 装配约束。
- [x] 补充路由测试源码：注册顺序不改变请求渠道，选定策略异常不向其他渠道提交；同步渠道 README 与架构说明。
- [ ] 按暂停验证要求，未运行 IDEA 检查、构建、测试或实际渠道调用；本轮未新增或宣称完成任何支付产品适配。


## 2026-10-26 Acquire A2 后台恢复

- [x] 源码：恢复游标/严格业务键、DO/MapStruct/Gateway、条件更新与独立 Flyway；A2 预占与首轮 native 任务同库事务提交。
- [x] 源码：原生 Handler 校验租户/登记任务/租约/代次，固定 order→allocation→cursor 锁序；锁外复用原输入恢复/原受理/查询，结果沿 A3 收敛。
- [x] 源码：后台原归属只授予续办权限，原会话离线/到期不阻断已提交意图；缺输入继续 HELD，终态必须精确回执；后续任务与游标原子登记，不跨渠道重扣。
- [x] 测试源码及文档：A2 登记失败回滚、事务内登记/锁外 RPC、旧代与并发续排、缺输入与到期关闭、缺回执拒绝、原权限/租户/租约与 generation 契约。
- [ ] 按用户要求未运行 IDEA/构建/测试/DDL/调度器/RPC 或外部渠道验收；新增 schema 尚未执行。
- [ ] 订单到期关单自动续办、首次准备仍在途且尚不能查回输入的最终收敛、native FAILED 运维恢复、JSAPI 交互、其他渠道/通知生命周期/交易重建/对账与完整目标继续。


## 2026-10-26 订单自动到期与关单续办

- [x] 源码：A1 与第 1 版到期任务、延期与新 deadlineVersion 任务同事务登记；原订单锁下淘汰旧版，直接复用原 deadline_version。
- [x] 源码：原生到期 Handler 按 CLOSE/KEEP_PENDING 处理，CLOSE 原子落 C0/任务，KEEP_PENDING 精确状态推进并保留续付；终态不新写。
- [x] 源码：关闭鉴权 Facade 与共享编排分离；C0 与首轮任务、关闭扫描游标与下一代任务各自原子提交，按原请求/scope/epoch 锁外执行 RPC，原权威证明决定 CLOSED。
- [x] 源码：DirectPay 延期恢复原创建元数据；关闭轮询配置/客户端契约说明与数据库/详细设计同步，无新增业务表或 RPC。
- [x] 测试源码：创建/延期/关单任务失败回滚，延期重放不重排，到期版本与策略，原关闭意图/代次/租户/锁外 RPC，缺收敛证明或仍有本地预占时禁止 finish。
- [ ] 未运行 IDEA/构建/测试/数据库/调度/RPC 验收；旧库已存在记录的补登记仍未实现，没有 DDL/部署操作。
- [ ] native FAILED 运维恢复、首次准备仍在途的精确收敛、JSAPI 交互、其他渠道/通知生命周期/交易重建/对账及完整目标继续。


## 2026-10-26 Acquire FAILED 任务审计恢复

- [x] 源码：限定三类 acquire 原任务的 detail/retry Facade 与运营 HTTP；专用 SERVICE/权限/可信租户，查询不泄漏 native 错误或原付款载荷。
- [x] 源码：订单锁下复用原 held/回执、deadlineVersion、关闭意图/代次检查；仅精确 FAILED 快照可原号重试，不补建/改期/取消或直接执行 Handler。
- [x] 源码：native 重试与不可变操作回执同库事务，scope/requestKey 首次回放/改参冲突；过期代次或已结束业务只审计，不唤醒。
- [x] 源码及测试源码：权限/预期状态/原任务命名空间/旧代/未来期限/重放跨失败周期/审计失败回滚/金融权限不扩大，领域约束、迁移和文档同步。
- [ ] 未运行 IDEA/构建/测试/DDL/HTTP/JWT/调度或真实 DB 原子性验收；未签发令牌，没有部署或重试实际任务。
- [ ] payment/refund FAILED 运维、原任务列表/告警、旧库补登记、首次准备在途竞态、JSAPI 交互、其他渠道/通知生命周期/交易重建/对账及完整目标继续。


## 2026-10-26 Payment/refund FAILED 原任务审计恢复

- [x] 源码：付款/退款 detail/retry Facade 与运营 HTTP、专用 SERVICE/权限/可信租户，API 只接受 COLLECTION/REFUND_REQUEST 原业务身份。
- [x] 源码：复用父门禁/付款或固定退款计划锁序与原调用绑定、原游标/期限/终态检查；仅精确 native FAILED 快照可原号重试，不触达渠道执行或新金融身份。
- [x] 源码：native 重试与不可变操作回执同库事务，完整重放回放首份决定、改参冲突，旧代/终态只审计，完成游标与非终态矛盾拒绝。
- [x] 源码及测试源码：补强退款 reference/请求/父订单/商户/摘要绑定；两类任务、权限、原条件、跨失败周期重放、未来期限/损坏、native 竞争/审计回滚测试，迁移与文档同步。
- [ ] 未运行 IDEA/构建/测试/错误码规范门禁/DDL/HTTP/JWT/调度或真实 DB 原子性验收；未签发令牌，没有部署或重试实际任务。
- [ ] 任务列表/告警、旧库补登记、首轮准备在途竞态、JSAPI 交互、支付宝 H5/微信付款码/银联资料、通知生命周期/交易重建/对账及完整目标继续。


## 2026-10-26 架构与设计 Review

- [x] 完成全项目模块依赖与关键交易/恢复/投递路径静态审查，结论、源码依据、优先级和验收条件见 [架构 Review](design/review.md)。
- [ ] 按 review 批次处理真实数据库一致性验证、运营查询锁隔离、恢复生命周期与内部接口收敛；本轮未修改业务代码。
- [ ] 本轮未运行 IDEA 检查、构建、测试、DDL、部署或真实渠道验收；审查结论不等于运行验证通过。


## 2026-10-26 按 design/review.md 实施 A 批（进行中）

| Review | 当前进展 | 验证状态 |
|---|---|---|
| R1 / P1 | 新增 payment-infrastructure 真实 MySQL/Testify/Testcontainers IT 源码，覆盖恢复游标/native 登记、竞争、快照与审计重试原子性；实际迁移/Mapper/Gateway，无数据库替身 | 未编译/未执行；Y2/退款容量补充见后续条目；A2/A3、Outbox、投影及完整恢复仍待补，R1 未验收 |
| R2 / P1 | acquire/payment 详情拆为 readInspection + 只读 RR 快照；retry 走 lockForRetry，原金融锁与原 native 条件复核保留；游标非锁定 find、退款只读重建已补 | 测试源码已补，未运行；尚无轮询负载/真实锁竞争结果 |
| R3 / P1 | 恢复退避/抖动、并发限制、业务指标、未知升级及保留策略待下一批 | 未实施/未演练 |
| R4–R7 / P2 | 仍待后续切片；R2 仅新增语义明确的 readSnapshot，没有静默改变原 read 或抽取通用恢复引擎 | 未完成 |
| R8 / P2 | 仍待根能力矩阵和全项目架构门禁收敛 | 未完成 |
| R9 / P3 | 按 review 保留条件性规划，待数据量/查询计划证据 | 未做性能实测 |

- 按现有暂停验证要求，没有启动 IDEA/构建/单测/Docker/数据库/部署/真实渠道；IT 的 mysql-it profile 需显式运行。
- 下一步继续补 R1 的关键真实业务边界测试源码；A 批的“真实数据库证明”停止条件尚未满足，不宣称 review 已全部处理。


## 2026-10-26 Review R1：Y2 与退款容量真实数据库测试源码

- [x] 源码：新增 FinancialPersistenceFixtures 与 FinancialPersistenceIT，复用实际 Flyway/生成 Mapper/Gateway/事务管理器及现有 mysql-it profile，没有数据库替身。
- [x] 源码：实际 PaymentSubmissionPreparation 双调用在远端准备后竞争，断言单一 ticket/SUBMITTING/epoch/原请求号；ticket 丢失后的重新准备无新授权，实际 attempt 写失败回滚。
- [x] 源码：已有成功容量前置数据下，真实父门禁/容量锁、FIFO 和 RefundGateway 的并发计划不超预占，header/plan/child/capacity 整组回滚。
- [x] 测试范围说明：额度/风控/渠道 IO 为测试替身；未使用的结果事件组件不被调用。这不证明真实渠道、完整退款准入、成功入账生成容量或结果 Outbox 原子性。
- [ ] 未运行 IDEA/编译/单测/Docker/MySQL/IT；R1 仍无本轮通过证据，没有执行 DDL、部署或真实金融调用。
- [ ] 继续补 acquire A2/A3、结果 receipt/Outbox 故障回滚、渠道成功而 ACK 丢失后的完整恢复、投影并发，以及其余 R1 清单；A 批尚未验收，不提前宣称 review 已完成。


## 2026-10-27 Review R1：Acquire A2/A3 与真实 Outbox 测试源码

- [x] 源码：新增 acquire 的显式 mysql-it profile、Testify/MySQL 容器测试配置与真实 A1 证据输入，调用实际 A2/A3 编排/Codec/Gateway/native task/Outbox。
- [x] 源码：并发相同 actionKey 只保留一份预占/allocation/游标/任务/快照；真实 Outbox publish 后异常验证 A2 整组本地回滚与不提前调用 payment.accept。
- [x] 源码：A3 金额/结果 receipt/费用确认及调整/Outbox 故障共同回滚、原成功事实再查证、并发/重复事实单次生效、冲突证据拒绝。
- [x] 业务修复：订单仓储重建 DIRECT_PAY 改用其原元数据与摘要，补 ORDER/DIRECT_PAY 两组真实重建测试输入；没有兼容分支或新业务迁移。
- [ ] 尚未运行 IDEA/编译/单测/Docker/MySQL/IT；没有执行 DDL/部署/实际 gRPC/渠道/商户发送。R1 仍无本轮通过证据。
- [ ] 继续投影版本并发、支付侧结果 receipt/Outbox 与完整恢复故障组合及其他 R1 清单；A 批未验收，其余 review 与完整业务目标仍进行中。


## 2026-10-27 Review R1：Realtime 投影版本与 receipt 测试源码

- [x] 源码：realtime-infrastructure 补显式 mysql-it profile、Testify/MySQL 容器与 Flyway 配置，默认单测不启动 IT。
- [x] 源码：四类实际投影入口各覆盖相同 delivery 并发、同版本内容冲突、冲突首次插入竞争、乱序版本、receipt 后故障和原 ACK 重放，共 20 个参数化输入。
- [x] 源码：实际 Gateway/Mapper/事务执行投影与 receipt 写入，测试故障仅在真实 insert 后注入；没有生产数据库结构变更。
- [ ] 未运行 IDEA/编译/单测/Docker/MySQL/IT；R1 尚无本轮通过证据，未验证真实消息网络、上游投递、投影重建或查询负载。
- [ ] A 批继续支付侧结果 receipt/Outbox 原子回滚与完整恢复组合故障；R3–R8 仍按 review 后续推进，A 批停止条件尚未满足。


## 2026-10-27 Review R1：Payment Y3 与四目标 Outbox 测试源码

- [x] 源码：新增 PaymentResultPersistenceIT，共 10 个测试输入，使用实际 progress/Y3/金融锁序/Gateway/Mapper/事务/Outbox；Y2 已提交作为合法持久化前置数据。
- [x] 源码：claim、capacity、成功状态、receipt、Outbox 后分别注入异常，验证整组本地回滚；保留远端成功，恢复只查询原身份/原请求/原 epoch，不重新创建 attempt 或获取首提权限。
- [x] 源码：相同 receipt 并发、同事实不同 receipt、冲突 receipt 的异常去重、跨租户收款流水唯一归属，以及实际权威关单回执。
- [x] 业务修复：开发期 payment_channel_receipt source CHECK 补 CLOSE_RESPONSE；test/ci 关闭 Outbox worker，仅观察实际持久化，不触发发送。
- [ ] 未运行 IDEA/编译/单测/Docker/MySQL/DDL/IT；没有实际渠道、回调验签或 gRPC 断链证明，R1 尚无本轮通过证据。
- [ ] A 批继续真实付款/退款恢复 handler、租约到期与旧新 generation 并发组合；R3–R8 仍按 review 推进，完整业务目标继续。


## 2026-10-27 Review R1：付款恢复 handler 的原生租约交错测试源码

- [x] 源码：PaymentResultPersistenceIT 增加 3 个组合用例，共 13 个输入；实际 INIT 受理/原任务同事务登记，再建立已提交 Y2 前置状态，使用真实 claim/handler/finish/游标/Y3/Outbox。
- [x] 源码：过期及已重新 claim 的旧快照拒绝；旧 worker 原查询在途期间新 worker 接管并提交唯一后继，当前 generation 完成后旧查询不能倒退状态或追加后继，旧 token ACK 失败。
- [x] 源码：未来 generation 故障任务不得产生渠道 IO 或跳过当前任务；当前任务仍按原 identity/requestNo/epoch 查询收敛，初始创建/首提 preparation/action/submit 均不调用。
- [x] 源码：测试专用 Clock 同时控制业务与原生租约，用屏障交错，不 sleep、不手改任务表、不变更生产轮询/租约配置；测试尾部只取消未完成任务，不删除资金证据。
- [ ] 未运行 IDEA/编译/单测/Docker/MySQL/IT；实际 Scheduler/Dispatcher 扫描、跨进程崩溃和真实渠道网络尚未验收，R1 尚无本轮通过证据。
- [ ] A 批继续退款实际 handler 的对应组合及其他剩余 R1 证据；R3–R8、缺失渠道产品资料与整体业务目标仍继续。


## 2026-10-27 Review R1：退款结果与真实 handler 租约交错测试源码

- [x] 源码：新增 RefundRecoveryHandlerPersistenceIT，共 8 个输入；实际付款 Y3 生成成功流水/容量后，实际 FIFO/预占/汇总/原生任务登记，再建立已首提退款的合法前置状态。
- [x] 源码：退款 claim、request/child/capacity、汇总与各目标 Outbox、receipt 四处写后注入异常，整组本地回滚，原占用不释放；真实 handler 可按原账户/退款号/金额恢复。
- [x] 源码：过期与已接管 claim 拒绝、旧查询在途与当前后继 generation 交错、旧 token ACK 失败、未来 generation 不得跳过当前任务，以及相同退款事实的不同 receipt 重放。
- [x] 源码：原付款 SUCCEEDED/金额/版本不变，退款容量与订单退款汇总单次结算，实际交易/费用/额度/商户 Outbox 不重复；canRefund 本地事务内、recoverRefund 事务外，首提分支零调用。
- [ ] 未运行 IDEA/编译/单测/Docker/MySQL/DDL/IT，未验证真实渠道退款幂等/验签、实际扫描器或跨进程崩溃；R1 仍无本轮通过证据。
- [ ] A 批继续多付款 FIFO 串行退款等剩余边界；R3–R8、缺失渠道产品资料与完整业务目标继续，不能以测试源码完成代替 A 批验收。


## 2026-10-27 Review R8：当前能力矩阵与首批依赖门禁源码

- [x] 文档：根 README 统一源码/自动化/环境验收/责任模块矩阵，删除“仅契约层/无业务服务”等过时当前描述；保留未实现渠道、授权、恢复、投影与对账范围。
- [x] 文档：ARCHITECTURE 的实现状态/Ping 差异表同步更新；ROADMAP 收敛未完成队列，BUSINESS-IMPLEMENTATION 改为实施日志，旧总览原文保存到 design/history。
- [x] 源码：现有 channel-core 测试目录新增 PayModuleBoundaryTest，扫描六服务及 profile POM，覆盖纯净层、跨服务、app→实现、渠道插件和存储适配角色，包含合成正反例。
- [x] 小范围职责修复：OrderRefundSummaryEventDTO 拥有事件类型常量，producer/codec 共用原 wire 值；codec 不再引用 app.service，仅消除协议常量依赖，不改变交易状态机。
- [ ] 未运行 IDEA/编译/单测/门禁/Docker/DDL/部署；没有新增任何通过证据。R8 仍需实际正反例/字节码依赖证明及技术入站适配接口收敛。
- [ ] R1/R2 真正数据库与锁竞争验收仍待运行，R3–R7、缺失渠道产品与完整业务目标继续；R8 文档/源码不替代前面批次的停止条件。


## 2026-10-27 Review R3：付款/退款有界退避与抖动源码

- [x] 源码：新增纯领域 RecoveryPollingPolicy，按原付款 initiatedAt / 退款 plannedAt 年龄指数退避、有界饱和与确定性身份/generation 抖动，不读取 Spring/ORM 或授予执行权。
- [x] 源码：两种恢复 handler 后继登记共用策略，保持原本地游标/native task 事务、锁序、原请求号、占用和终态规则；传输失败仍由原生 retry/FAILED 处理。
- [x] 配置：新增共享 recovery-polling 四项，默认 30 秒、1800 秒上限、900 秒年龄档位、20% 抖动；删除旧 payment/refund 固定轮询属性及环境变量，无兼容读取。
- [x] 测试源码：年龄/饱和/不溢出、固定抖动及窗口、时钟回拨、非法配置、Spring 装配和两种长龄 handler；真实租约交错 IT 显式零抖动并接入新策略装配。
- [ ] 未运行 IDEA/编译/单测/MySQL/IT/迁移/部署；无实际请求速率或任务生成量证明，R1/R2 停止条件仍未满足。
- [ ] R3 尚需渠道并发、业务指标、长期未知升级及证据保留/归档；R4–R8、渠道/输入/通知/投影/风控/对账等完整业务目标继续。


## 2026-10-27 Review R3：单 JVM 按渠道共享恢复舱壁

- [x] 源码：ChannelRecoveryConcurrency 复用 LoadUp ResilienceRegistries/BulkheadRegistry，所有渠道名由 enum 生成；统一 Facade 的 query/recover/close/queryRefund/recoverRefund 共用每渠道预算，满额先于 Provider IO 拒绝。
- [x] 源码：原路由/租户/绑定校验优先，异常及无效返回释放名额；不换账户/请求号、不降级到其他渠道、不以拒绝或超时释放占用。
- [x] 装配：channel-core 仅依赖框架 API，payment-business bootstrap runtime 加 core binder，标准 native 配置默认每渠道 16 / 0ms；不创建自有并发框架或线程池。
- [x] 测试源码：五类恢复入口共享预算、实际线程交错和渠道隔离、失败/无效响应后释放、首提和本地检查不丢票据、拒绝阻塞配置，以及 API/binder runtime 依赖门禁。
- [ ] 首提 submit/submitRefund 仍须后续在 Y2/退款提交边界前完成并发准入；本切片不在已赢得持久票据后引入额外拒绝点。集群总配额/QPS/公平性仍未实现。
- [ ] 未运行 IDEA/编译/单测/门禁/多实例/渠道/部署；无吞吐或资源占用实测，R1/R2 验收和 R3 业务指标/升级/证据保留仍待完成。


## 2026-10-27 Review R3：恢复 handler 执行观测源码

- [x] 源码：RecoveryExecutionMetrics 复用共享 MeterRegistry，仅 kind/payment-refund 与固定 outcome 的 8 个 Timer、2 个在途 Gauge，不带租户/业务/任务/账户/金额/URL/异常文本标签。
- [x] 源码：真实 Payment/RefundRecoveryTaskHandler 在整个处理外层记录，恢复服务返回已提交续行决策；计量不进入本地重试闭包，原异常继续交给 native retry/ACK。
- [x] 源码：无共享 registry 不创建私有 backend，不跳过业务；调用期指标 RuntimeException 不覆盖原业务异常，不依据观测改写资金终态/占用/权限。
- [x] 测试源码：业务后计数、失败原样抛出、固定标签、并发在途归零、无 registry 继续执行业务及实际两类 adapter 接线；服务决策断言和 IT 装配已同步。
- [ ] 未运行 IDEA/编译/单测/IT/exporter/部署；没有实际指标采集结果，不能将 Timer.count 或 inflight 冒充 UNKNOWN 库存或资金占用。
- [ ] R3 继续真实 UNKNOWN/金额/最老年龄/FAILED/Outbox 快照指标、阈值升级与证据保留，首提/集群总配额亦未完成；完整业务目标保持。


## 2026-10-27 Review R3：UNKNOWN 只读库存及样本新鲜度源码

- [x] 源码：RecoveryInventoryReaderImpl 使用现有生成 Mapper/TableDef/QueryMethods，严格租户及实际只读 RR，分别聚合 UNKNOWN 付款/子退款数量、名义金额和最老创建时间，无写入/行锁/网络回查。
- [x] 源码：独立 ScheduledTask 只对配置范围采样，最多 100 个不同租户；全部成功才发布缓存。首次为 NaN，空库存显式 0，失败保留整体旧样本并暴露 sample.age/refresh failure，scrape 不查询数据库。
- [x] 配置：默认未启用，显式租户范围及 300 秒间隔；无共享 MeterRegistry 不查询/不建私有 backend。固定 kind/outcome 标签，不含租户或业务身份。
- [x] 测试源码：完整/失败/部分/空样本、scope/只读/周期/时钟回拨/无 registry、cell 类型/溢出，以及真实付款/退款聚合输入（相关 IT 当前 14/9 个输入）。
- [ ] 未运行 IDEA/编译/单测/MySQL/IT/exporter/调度/执行计划/负载/部署；不增加推测索引，不能宣称真实查询或 R1/R2/R3 验收通过。
- [ ] 本值不是额度 held/渠道余额或 firstUnknownAt；这些事实、FAILED/Outbox 库存、长期未知升级、证据保留和完整业务目标继续。


## 2026-10-27 Review R3：首次 UNKNOWN 持久时间与年龄口径

- [x] 源码：付款/子退款 DO 与初始 schema 增加 first_unknown_at；UNKNOWN 必须有值，首次状态 CAS 同事务写入本地微秒时间，回滚一起撤销。
- [x] 源码：重复查证与任务重领不重置，终态保留元数据；只读库存只统计当前 UNKNOWN，以最小 first_unknown_at 计算 .unknown.oldest.age，删除旧开发期创建年龄指标。
- [x] 测试源码：付款/退款实际 gateway 状态写入后的故障回滚、稍后首个提交、重复未知、终态保留及退出库存；Converter/Collector 对应映射和指标名已同步。
- [ ] 本轮未执行 IDEA/编译/测试/DDL/数据库重建/部署，不提供旧开发期数据兼容回填；R1/R2 运行证据与 R3 其他目标继续保留。


## 2026-10-27 Review R3：分级查证列表与告警规则源码

- [x] 源码：共享纯领域 RecoveryEscalationPolicy，默认 15 分钟 REVIEW、24 小时 URGENT，服务端可配置；级别不改变资金状态/占用/提交权。
- [x] 源码：独立 Facade/HTTP 只读列表，仅配置的受信运营 SERVICE + recovery read 权限，租户取凭证；付款/子退款身份及父任务代次可定位现有 detail/retry。
- [x] 源码：生成 Mapper/TableDef/联表投影 + MapStruct，实际 RR/readOnly、无排他锁/网络/COUNT，时间与 ID keyset 每页最多 100+1；缺失游标显式空元数据，未来时间在 NORMAL 保持可见。
- [x] 源码：阈值与采样间隔 Gauge、独立 Prometheus 普通/紧急互斥及样本不可用规则，不叠加重复副本库存。没有更改现有 release 或开启 Alertmanager。
- [x] 测试源码：领域/配置、鉴权/租户/分页/回拨/损坏结果、实际付款同时间游标/跨租户/并行金融写入及退款父任务联表，Prometheus 阈值/新鲜度/禁用/target-down 用例；当前付款 IT 15、退款 IT 9。
- [ ] 所有源码未编译/运行；未执行 promtool、数据库、查询计划、真实 JWT/exporter/规则评估、部署或人员通知；R1/R2 运行证据仍未满足。
- [ ] R3 继续实际 held/FAILED/Outbox、首提/集群准入、实际告警路由/人工工单、证据保留及长期故障演练。契约见 design/RECOVERY-ESCALATION.md。


## 2026-10-27 Review R5：明确上下文与本地事务语义

- [x] 源码：acquire/payment/product/limitcenter 的上下文-only read 改为 withTenantContext；realtime 的真实只读 read 改为 readSnapshot，五个 helper 的 write 改为 writeLocal。
- [x] 源码：生产、单测、真实 MySQL IT 的对应调用方同步；不保留旧方法/适配重载，不增加跨模块执行器、依赖、配置或数据库格式。
- [x] 语义：原 REQUIRES_NEW/RC 写、RR 快照、最多总计三次重复键/瞬态数据库失败尝试、业务错误/空值映射、租户恢复及拒绝外层事务保持；渠道 IO 原位置不移动。
- [x] 测试源码：补五个 helper 的上下文/只读/隔离级别、回滚后重试、上限、业务失败不重试、嵌套拒绝和模块映射；见 design/TRANSACTION-BOUNDARIES.md。
- [ ] 按用户要求未运行 IDEA/构建/测试/DDL/网络；未新增运行通过证据，不用 helper SPI 替身证明真实 MySQL 回滚，R1/R2/R5 完整验收保留。


## 2026-10-27 Review R4：付款与 attempt 共同绑定源码

- [x] 源码：纯领域 PaymentAttemptBinding 集中付款身份、Money、商户、额度 operation、原 caller 与 paymentDigest，区分 active/submittable/observable/closable/原 winner 请求；不授予 Y2 提交权。
- [x] 源码：payment 内错误映射适配接入首提、恢复、Y3、回调、关单、渠道请求、动作、幂等请求重放和结果投递；冻结 route 比较也集中，保留锁序/权限/协议/证据检查。
- [x] 语义：Y3 错误目标仍走冲突 receipt/anomaly，正确目标持久绑定损坏仍直接 SNAPSHOT_INVALID；允许已提交请求在终态/门禁后观察，不转换成新的提交权限。
- [x] 测试源码：六类独立绑定篡改、各模式与错误映射、路由、迟到/终态、冲突顺序；扩充 Y3 app 测试确认损坏绑定无资金写入，尚未执行。
- [ ] 本轮未运行 IDEA/编译/测试/数据库/渠道。R4 完整门禁上下文与所有入口组合验收、R1/R2/R5 运行证明继续保留；见 design/PAYMENT-BINDING.md。


## 2026-10-27 Review R4：完整门禁执行上下文源码

- [x] 源码：纯领域 PaymentGateBinding 一次核对父/付款门禁，LockedPayment 只持有已校验绑定；删除旧原始三参数构造并同步调用。
- [x] 源码：PaymentExecutionContext 组合门禁/attempt 绑定，要求相同完整付款快照，分别表达 active/submittable/observable/closable。
- [x] 源码：锁内 Y2/恢复/关闭/Y3/动作/投递与 outcome 查询接入；保持原锁序，增加无事务/只读事务在锁网关前拒绝及原请求 ID 复核。
- [x] 语义：双 OPEN 只约束首次提交；关闭/终态的已提交事实可观察；Y3 错误目标仍先写冲突，不把上下文当 Y2 票据或负面资金证据。
- [x] 测试源码：门禁十类篡改、不同快照混用、fence/终态模式、错误映射/锁序/事务拒绝，以及实际 app 的 Y2 拦截，无输入绑定/金融更新/无收款证据制造。
- [ ] 本轮未运行 IDEA/编译/测试/数据库/渠道；R1/R2/R4/R5 的真实运行验收和 R6 冻结意图收敛仍待完成，见 design/PAYMENT-BINDING.md。


## 2026-10-27 Review R6：付款持久意图的独立快照源码

- [x] 源码：acquire/payment 各自 PaymentIntentSnapshot，独立 format 标识、完整嵌套本地条款、扁平 choice、原期限字符串；不再直接序列化/读取公开 Command 或 DTO 字段类型。
- [x] 源码：标准 MapStruct 显式转换，固定 Jackson 存储配置；当前 Bean Validation 仅用于 encode，decode 由快照自身和落库索引验证，不查询当前合约/路由填值。
- [x] 语义：原 long 分额、CNY、条款 hash/version/generation、输入引用和原绝对期限保留；未知/旧/其他 owner 格式直接失败，无兼容/回填/重扣或释放逻辑。
- [x] 测试源码：API JSON 命名/注解隔离、恢复不调用当前 Command Validator、旧/损坏/不精确格式拒绝、完整映射及深层集合不可变；当前 Spring/IT 装配继续通过 converter 扫描使用新类型。
- [ ] 本轮未运行 IDEA/编译/MapStruct/测试/数据库/部署，未重建或改写现有开发数据。R6 当前源码不支持旧裸 Command payload；详见 design/PAYMENT-INTENT-SNAPSHOTS.md。


## 2026-10-27 Review R7：模块内恢复协议源码

- [x] 源码：RecoveryTaskKey/RecoveryPosition 共用规范身份、代次与单调后继；原 typed key/cursor 保留，bizId/schema 不变。
- [x] 源码：RecoveryTaskProtocol 对显式可信租户、任务领取元数据、RUNNING/attempts/maxAttempts、到期/有效租约及业务 current 统一错误映射；不做 IO、claim、ACK 或渠道推进。
- [x] 源码：付款/退款两阶段都接相同规则，推进后补同代 dueAt 复核；原锁序、事务外执行、终态判定、原请求和 successor 同事务仍独立保留。
- [x] 测试源码：两条业务类型共享协议用例、旧/未来/完成/错主体/错时间、正向代次/溢出/heartbeat/元数据；两类服务补推进后同代时间异常场景。
- [ ] 本轮未运行 IDEA/编译/测试/数据库/多实例/渠道，不以元数据检查替代 native token fencing 或业务 Y2；完整协议与 R1/R2 真实运行证明继续保留，见 design/RECOVERY-PROTOCOL.md。

## 2026-10-27 支付宝直连 H5 动作

- [x] 源码：按官方手机网站协议使用 WAP/QUICK_WAP_WAY；独立支付宝模块生成原号签名 GET 动作，经现有 Facade/codec/MapStruct 返回 REDIRECT。
- [x] 源码：本地动作与渠道观察分离，payment_action 来源明确、没有伪造回执，原门禁/epoch 与截止时间约束；UNKNOWN 可读取有效原动作。
- [x] 测试源码：无 HTTP/无资金回执、固定参数、服务商令牌拒绝、网关与动作篡改及原动作呈现。
- [ ] 编译、测试、实际数据库、浏览器及真实渠道验收均未执行；服务商 H5 与丢失本地动作恢复未完成。详见 [H5 契约](design/ALIPAY-H5.md)。

## 2026-10-27 微信 V2 付款码首提与查证

- [x] 源码：按官方 V2/XML/HMAC 接入直连与服务商首提，独立 APIv2 密钥和 HttpTemplate 命名操作；统一 Facade/微信插件内选支付方式。
- [x] 源码：安全输入复用、原固定金额/期限/账户、USERPAYING 与 UNKNOWN、成功后原号 V3 查证统一事实；MICROPAY 与未知产品不调用 V3 close。
- [x] 测试源码：官方 HMAC 向量、严格 XML 与签名、密钥边界/擦除、账户/金额/交易号防错及关闭产品门禁。
- [ ] V2 撤销/mTLS/持久补偿与失 ACK 恢复未实现，不能声称完整付款码闭环；构建、测试和真实环境验收均未执行。详见 [付款码设计](design/WECHAT-CODEPAY.md)。

## 2026-10-27 付款生命周期快照生产

- [x] 源码：PaymentResultEvents 重命名 PaymentEvents；受理 INIT 0、attempt 选路 PROCESSING、UNKNOWN 与 CLOSING 的真实付款版本同事务写完整 realtime Outbox。终态保留独立四目标。
- [x] 源码：原绑定/成功 claim 时间及租户/可写事务检查；版本不变、重放及本地动作保存不增发。UNKNOWN accept 重放保留原动作展示。
- [x] 测试源码：生命周期/幂等/只投投影、UNKNOWN 实际存储与 Outbox 写后故障回滚，以及 realtime version 0/迟到 INIT/终态门禁 IT。
- [ ] 所有构建、单测、MySQL IT 与真实消息投递均未执行；重建/检查点与查询新鲜度仍待完成。详见 [生命周期快照](design/PAYMENT-LIFECYCLE-SNAPSHOTS.md)。

## 2026-10-27 对账 client/domain 与核对核心

- [x] 用户确认：支付宝/微信官方交易日账单、收款与退款、实际收款账户/账期分批；银联后续。
- [x] 源码：两层实际模块、Facade/DTO 契约、INIT 批次/原运行/完成幂等、双侧封存/重复/总额/规范化哈希校验、纯 merge 与多原因差错；根 reactor 和边界门禁同步。
- [x] 测试源码与 [独立四表设计草案](design/sql/reconciliation-draft.sql)已补。未构建、运行或执行 DDL。
- [ ] 来源范围完整性、File/Transfer 实际解析、数据库/应用/任务与权限、差错查证/复核、部署及所有真实验收仍待完成；[详细设计](design/RECONCILIATION.md)区分现有源码与后续契约。

## 2026-10-27 P1：对账应用、独立存储、接口与微信 ALL 格式

- [x] 源码：ReconciliationService 实现 Facade，受信 SERVICE/租户/原 caller/账户目录逐项鉴权，create 幂等、双侧封存引用、start 原运行与 DelayedTask 同库登记；start 使用写 Command。
- [x] 源码：四表 BaseDO/生成 Mapper/TableDef 消费、Spring MapStruct 和独立 Flyway 迁移；输入只封存、批次 CAS、完整差错和结果摘要同事务提交。读取 RR/无资金回写，纯比较在事务外。
- [x] 源码：HTTP 运营接口、提供方 Protobuf/gRPC 批次接口和入站 JWT；七层模块及独立 bootstrap/business profile，七应用构建/部署选择器、清单、Docker 允许路径与边界门禁同步。
- [x] 源码：微信 2026-01-16 API ALL 格式解析，实际商户/子商户/日期、字节/行数上限、完整尾部七项合计、CNY 原订单/申请退款金额、状态与重复键；REVOKED/未知格式整份拒绝，来源真实性仍须外层证明。
- [x] 测试源码：批次任务编排/失败顺序、JWT 拒绝、原账户授权/撤销、协议 optional/int64/日期/未知符号、持久化映射和完整文件/坏文件边界；分页复用 PageResult。
- [ ] 未运行 IDEA、编译、MapStruct/Protobuf 生成、单测、MySQL、DDL、脚本、容器、部署或真实渠道验收；上述是源码进度。
- [ ] P1 继续官方账单下载/可信 File/Transfer 接入、支付宝解析及 payment 权威范围导出；支付输入/关单证据收敛、服务商 H5、微信撤销补偿、收银台 JSAPI 仍保留，未将整个 P1 标完成。

## 2026-10-27 P1：File/Transfer 手工渠道文件导入

- [x] 源码：上传/提交/详情/原 FAILED 重试 HTTP + Facade，SERVICE/租户/文件 owner/批次原 caller/当前账户权限；sourceSha256 绑定原字节，来源固定 MANUAL_UPLOAD，无任意 parser/账户/LEDGER 完整性入口。
- [x] 源码：reconciliation_import 独立意图/来源表、原生任务与永久业务引用同库提交；原请求幂等、每批次一个 INIT 导入、原账号标识/hash/profile 冻结，禁止并发替换来源。
- [x] 源码：Transfer 原始任务与唯一 option 查证，RR 读绑定、事务外原字节/长度/hash/格式核对，本地事务统一提交封存全行、批次引用和逻辑 SEALED；无部分输入或伪造空成功。
- [x] 源码：短读/I/O 保留 INIT、确定坏文件 REJECTED、原任务受控重试；SEALED 与 native taskStatus 独立，重复处理核对原 manifest，长期文件引用保留证据。
- [x] 装配源码：复用 Transfer/File 应用和 S3 binder，目录 statement-account、上传/批次上限与本地 Scheduler 配置；无真实 bucket/密钥生成或外部调用。
- [x] 测试源码：模型、实际字节和长度、任务/选项防错、并发登记、原请求重放、原子失败顺序；MySQL IT 覆盖 File/Transfer/业务登记与封存回滚、原任务重试、坏尾部和跨租户，只有外部对象字节替身。
- [ ] 未编译/运行上述单测或 IT、DDL、容器、S3、HTTP/JWT/调度或部署。原生 Transfer 崩溃遗留 RUNNING 无租约恢复，不做无条件重排；证据保留清理仍待继续。
- [ ] P1 继续官方账单自动下载/验签来源、支付宝解析、payment 权威范围导出，以及支付异常收敛、服务商 H5、微信撤销补偿和收银台 JSAPI；完整目标仍未完成。

## 2026-10-27 P1：退款渠道受理时间证据

- [x] 源码：RefundObservation 增加可空 acceptedAt；微信已验签提交/查询严格解析必需 create_time，按注入 Clock 的 zone 转本地微秒；原号恢复保留该时间，不以本地提交或 success_time 代替。
- [x] 源码：独立 refund_acceptance_evidence/BaseDO/生成 Mapper/Spring MapStruct/Gateway；只在已匹配的 APPLIED/IGNORED 回执之后、原退款锁与同一事务记首次时间，保留原回执与完整账户/请求/金额绑定。
- [x] 语义：不改付款、退款聚合版本或审计时间，不新增资金成功/释放判断；后续不同时间只记异常，不覆盖首次值。退款前于原收款的时间拒绝；无该字段不制造时间。
- [x] 源码：事实摘要改 refund-facts-v2 并包含 acceptedAt，构造调用方同步，不保留旧签名或旧摘要兼容；支付宝当前明确不推测受理时间。
- [x] 测试源码：协议日期/缺字段/跨日、摘要/微秒、资金/元数据分离、首次证据重放/冲突/权限；真实 MySQL IT 增加元数据写后故障统一回滚和聚合版本/审计不变。
- [ ] 未编译、运行测试/IT、执行 V20261027020000__refund_acceptance_evidence.sql、数据库操作或真实渠道调用。P1 权威导出仍须完整范围/日期/未知记录处置与源端 capture、RPC 和对账接线，不把缺失受理时间当空退款集合。


## 2026-10-27 P1：payment 权威采集快照与对账 LEDGER 接线

- [x] 源码：payment 纯领域采集范围/行/完整读/INIT→SEALED或BLOCKED；渠道账单日转换到冻结系统 zone，不改全局时区；收款成功 claim 与退款渠道受理日期分别选集。
- [x] 源码：真实只读 RR/可配置预算，关联付款/attempt/历史绑定/原回执/退款终结 claim；缺账户/未决收款/缺退款日期/时间冲突/超限或溢出明确阻断，无部分行与假零成功。
- [x] 源码：独立采集头/行两表、Flyway/DO/Spring MapStruct/生成 Mapper 消费，INIT与原 DelayedTask 同事务；全行与终态 CAS 同事务，后续分页只读首次封存快照，不再读移动财务表。
- [x] 源码：PaymentCaptureFacade/PaymentCaptureRpc Create/Detail/Lines/Retry，optional/int64/严格 presence/微秒时间/分页 EOF；固定 reconciliation SERVICE与权限、原 caller/租户、native状态与逻辑状态分离、原 FAILED 条件重试。
- [x] 源码：ReconciliationLedgerFacade 与 HTTP prepare/import/retry；原批次账户目录/当前授权，服务端冻结范围发 gRPC；完整页/显式空 EOF/总额/hash/末尾原快照核验，不接操作者账务文件或自报完整性。
- [x] 源码：provider-owned 定长前缀二进制证据档案，原字节 hash 等于源 contentHash；事务外私有 File 上传，长期引用/LEDGER manifest与全行/批次关联同库事务；原 capture 重放不做远端 IO。
- [x] 装配源码：payment-client/rpc 正向依赖、Spring mapper/typed operations、payment channel/round_robin/deadline、单租户挂载 SERVICE token逐次读取；未签发令牌或改真实环境。
- [x] 测试源码：领域不变量/跨时区边界、协议 optional/int64/原任务、完整 hash/原档案/上限、编排身份/原请求/native元数据/重试、分页/坏源/授权撤销、预算；真实 MySQL 源 SQL/RR并发/新事实不改旧快照/全量封存 SQL 写后回滚及退款缺日期/跨日/状态/冲突。
- [ ] 按用户要求没有运行 IDEA、构建、Protobuf/MapStruct生成检查、单测、MySQL IT、DDL、文件/真实RPC调用、脚本或部署；上述只有源码进度，不构成 R1/R2 运行证据。
- [ ] P1 继续官方账单自动下载/来源校验、支付宝固定格式/日期与完整导出、差错复核/可信源查证；源准备与挂入目前显式两步，自动化运营与保留策略继续。
- [ ] 支付输入/关单异常证据、服务商 H5、微信 V2 撤销/mTLS/补偿、收银台 JSAPI 与其他原业务目标仍未完成；整个 P1 保持未完成。详见 design/PAYMENT-RECONCILIATION-CAPTURE.md。


## 2026-10-27 架构复审与 PostgreSQL 开发期切换评估

- [x] 更新 design/review.md：复核 R1–R9，区分源码改善与未执行的数据库验证；补充付款事件、恢复及对账采集/导入的架构结论。
- [x] 记录 P2：对账比较 native task 耗尽后批次仍 RUNNING，当前缺原 task/run 的受控重试入口；报告含证据、修复建议与验收场景，未改业务代码。
- [x] 按用户确认的无生产数据、无需兼容/存量迁移前提，盘点 44 份业务迁移与 70 个建表声明及共享依赖；给出 PG 单后端方案、事务/SQL/类型阻断项、收益及 16–28 人日粗估。
- [ ] 比较任务恢复修复及运行验证、PG 组件适配/DDL/真实集成验证均未实施；本轮未构建、测试或操作数据库。
