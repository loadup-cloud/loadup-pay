# Pay remaining work

v1.4已将D01—D10收敛为可实施设计。2026-10-21完成P0/P1源码及六应用脚本、JAR/镜像构建、OrbStack启动与健康验收，见[实施说明](IMPLEMENTATION.md)
。client/domain契约已补齐；15类37个单测源码尚未编译或执行。复用框架Money/JSON诊断；测试RPC及协议模块已删除，业务RPC按P2实际用例接入。P2首批已新增customer授权/资格的app、持久化和HTTP源码，详情见[customer说明](customer/README.md)
；本批4类22个新单测源码未编译/执行，数据库/JWT业务验收尚待完成。product持久准入、条款编译与HTTP首批源码也已补齐，见[product说明](product/README.md)
；新增6类33个测试方法源码尚未编译/执行。limitcenter激活/原子预占/查询及执行许可/校验/显式过期源码已补齐，两批新增10类55个测试方法源码未编译或执行，见[limitcenter说明](limitcenter/README.md)
。product初始额度发布/Outbox/ACK/失败重放源码已补，另新增7类31个测试方法源码未编译或执行。acquire 普通创单/查询、A0稳定意图与A1订单/快照/整单费用原子落库源码已补，新增4类23个测试方法源码未编译/执行，见[acquire说明](acquire/README.md)。已补原单会话签发/重放、摘要能力认证及只读托管页面，新增5类29个测试方法源码未执行。Checkout 一步创单及 A1 首次会话原子提交源码已补，新增14个测试方法未执行。payment 受理/查询、三表持久化及执行门禁检查源码已补，4类24个新增测试方法已编译但未执行，业务 bootstrap 编译通过，见[payment说明](payment/README.md)。payment 父门禁/收敛查询、单笔关闭/tombstone/未收款回执已补，新增22个测试方法已编译未执行；渠道未知仍保留CLOSING。以下保留尚未完成的执行与验收工作。

- [ ] 实现业务proto/HTTP适配、应用编排、数据库唯一约束/原子事务、事件和任务；补齐相应单测与业务集成验收。
- [ ] acquire：普通创单/Checkout/start之前批次已完成窄范围编译，结果刷新后续源码按用户要求暂不验证；仍需真实MySQL/JWT/HTTP验收，核对数据库第15—18节结构；商户start金额占用/服务调用与refresh权威结果落账源码已补；继续DirectPay、渠道执行/结果生产、后台扫描/事件、显式关单/未受理占用释放及有界恢复源码已补；原订单延期/幂等回执源码已补；继续自动关单、等待任务和Outbox通知。
- [ ] customer：执行新增模块的窄范围构建与单测；真实MySQL验收幂等/回滚、租户隔离、撤销后重授和资格并发；验证签名JWT与HTTP响应。主档维护入口继续按框架商户能力接入。
- [ ] 
  product：执行准入编排/持久化/HTTP及条款编译器的窄范围构建、单测与真实MySQL/JWT验收；初始额度策略可靠发布/ACK/重放源码已补；继续多revision/多产品共享策略演进、实际渠道及受控引用的发布校验，完成与acquire
  A0/A1集成验收。
- [ ] 费用：创单已冻结独立INIT order_fee和原总额规则；付清确认已接入 A3 同事务源码；继续退款累计基数与退费调整。
- [ ] payment：受理/查询及门禁检查已编译，完成真实MySQL/JWT/HTTP和并发验收；acquire 金额占用/accept调用及outcome权威结果刷新源码已补；敏感输入准备/固定引用/单 attempt 绑定及纯 API 接入源码已补；绑定管理/不可变身份版本/内部选路器源码已补；durable attempt/路由冻结/额度 Reserve-Begin-Verify/默认risk/Y2 CAS 源码已补；不可变版本化路由资源/按序选路源码已补；继续真实渠道提交/动作/结果/交易号claim、渠道关闭收敛及可信结果。
- [ ] payment退款子域：独立refund_request/plan/refund_order、FIFO固定分配/原付款可退预占，不更新订单生命周期和支付金额。
- [ ] limitcenter：激活/原子预占/查询源码已补，完成窄范围构建及真实MySQL并发/JWT验收；执行许可/校验/显式过期源码已补；继续实现payment
  Y2/可信事实、安全换attempt、确认/释放与权威事实核验/终态回执源码已补；继续过期扫描、退款恢复、父级共享规则及历史记录。
- [ ] cashier/SDK：原单会话签发/重放、摘要能力认证和终态只读页面源码已补，完成窄范围构建及真实JWT/HTTP/MySQL验收；继续手动金额/尾款校验、每笔动作幂等、PENDING续付、完整渠道交互及会话撤销管理。
- [ ] 支付宝/微信/银联及直连/服务商目标接入，默认CNY；按实际开通产品补齐渠道/沙箱/银联机构接口材料。
- [ ] PaymentSucceeded/OrderFullyPaid及关闭/退款独立通知，签名/语义ACK/重试/版本幂等；realtime组合订单、付款、退款视图。
- [ ] 默认放行风控与 Y2 前咨询源码已补；继续结果落库后异步通知及外部 provider 策略，详细协议后置。
- [ ] 后置reconciliation：独立文件/交易快照/逐笔核对/差错复核，不参与交易提交或直接改资金表。
- [ ] 如需多人长期使用Headlamp，接入OIDC/HTTPS；当前为本机短期token。

当前范围以最新修订为准：完成 PAYMENT_CODE（商户扫码枪输入）、JSAPI、H5；移除 Native 二维码和原生 APP 支付。纯 API 的 DirectPay 与 create→startPayment 均需完成，托管收银台为可选入口；付款码/JSAPI 安全输入准备及纯 API start/DirectPay 受理编排源码已补；继续真实渠道提交、输入清理和后台恢复。

全部业务的持续实施清单与当前继续点见 [BUSINESS-IMPLEMENTATION.md](BUSINESS-IMPLEMENTATION.md)。费用确认、额度确认/释放、敏感输入与纯 API 编排的新增源码尚未验证，不能视为全部业务已完成。

渠道绑定新增发布/暂停/永久撤销/查询、三表迁移、类型化账户与凭证版本、内部选路器和17个测试方法源码；未验证。Secret/产品开通校验、attempt 持久冻结与执行边界仍待完成，详见 [payment说明](payment/README.md)。

本批新增首笔 attempt 与 Y2 单次提交权、额度 HTTP 客户端、未提交关单及14个测试方法源码；尚未运行检查/编译/测试。实际发送、自动执行/查证、quota 终态投递和全部剩余业务继续推进。

2026-10-22 继续业务实现：新增不可变 RoutingProfile 的 client/domain/app/infra/adapter、发布/查询及一表迁移；Y1 选路强制读取原冻结 routingProfileRef，仅在同租户/实际收款商户、原合约渠道/方式/接入模式和方案有序白名单内选择 ACTIVE 绑定。方案不可覆盖，重放不重新启用绑定；新 attempt 冻结当前 revision，旧 attempt 保留原身份。新增22个测试方法源码，未运行 IDEA 检查、编译、测试、DDL 或部署。product 发布前引用/能力校验和真实渠道发送等仍待完成。

2026-10-22 编辑错误修复：依据用户“先修复编辑错误”，使用 IDEA MCP 检查当前192个改动 Java 文件；修复 OrderClosureConverter 两处 MapStruct 表达式的嵌套引号语法错误，改用 closeRequestKey 命名标量转换，保持 order-close/intentId 幂等键。修复文件 IDEA 复查无错误。其余7个仓储文件仍有未生成 MyBatis-Flex Mapper/TableDef 导致的解析错误；未手写生成类，未运行构建、注解处理或测试，不能将此次编辑器检查当作编译通过。

2026-10-22 Y3 续批：新增内部规范化结果应用、永久回执/资金差错、跨租户真实账户交易号唯一认领、同事务成功时间/退款容量与 acquire/limit/realtime/risk 四目标 Outbox。原本地未提交终态也接入可靠事件生产，重放不重复投递任务。新增四表迁移和25个测试方法源码；按用户暂停要求未执行任何检查/构建/测试/DDL/部署。真实渠道发送/验签/动作、自动调度/查证、四目标消费者、退款申请/执行和全部其他待办继续推进。
