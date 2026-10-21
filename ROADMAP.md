# Pay remaining work

v1.4已将D01—D10收敛为可实施设计。2026-10-21完成P0/P1源码及六应用脚本、JAR/镜像构建、OrbStack启动与健康验收，见[实施说明](IMPLEMENTATION.md)
。client/domain契约已补齐；15类37个单测源码尚未编译或执行。复用框架Money/JSON诊断；测试RPC及协议模块已删除，业务RPC按P2实际用例接入。P2首批已新增customer授权/资格的app、持久化和HTTP源码，详情见[customer说明](customer/README.md)
；本批4类22个新单测源码未编译/执行，数据库/JWT业务验收尚待完成。product持久准入、条款编译与HTTP首批源码也已补齐，见[product说明](product/README.md)
；新增6类33个测试方法源码尚未编译/执行。limitcenter激活/原子预占/查询及执行许可/校验/显式过期源码已补齐，两批新增10类55个测试方法源码未编译或执行，见[limitcenter说明](limitcenter/README.md)
。product初始额度发布/Outbox/ACK/失败重放源码已补，另新增7类31个测试方法源码未编译或执行。acquire 普通创单/查询、A0稳定意图与A1订单/快照/整单费用原子落库源码已补，新增4类23个测试方法源码未编译/执行，见[acquire说明](acquire/README.md)。已补原单会话签发/重放、摘要能力认证及只读托管页面，新增5类29个测试方法源码未执行。Checkout 一步创单及 A1 首次会话原子提交源码已补，新增14个测试方法未执行。payment 受理/查询、三表持久化及执行门禁检查源码已补，4类24个新增测试方法已编译但未执行，业务 bootstrap 编译通过，见[payment说明](payment/README.md)。以下保留尚未完成的执行与验收工作。

- [ ] 实现业务proto/HTTP适配、应用编排、数据库唯一约束/原子事务、事件和任务；补齐相应单测与业务集成验收。
- [ ] acquire：执行普通创单/Checkout/查询的窄范围构建与真实MySQL/JWT/HTTP验收，核对数据库第12节结构；继续DirectPay、多笔付款金额占用、可信结果落账、延期/自动关单、等待任务和Outbox通知。
- [ ] customer：执行新增模块的窄范围构建与单测；真实MySQL验收幂等/回滚、租户隔离、撤销后重授和资格并发；验证签名JWT与HTTP响应。主档维护入口继续按框架商户能力接入。
- [ ] 
  product：执行准入编排/持久化/HTTP及条款编译器的窄范围构建、单测与真实MySQL/JWT验收；初始额度策略可靠发布/ACK/重放源码已补；继续多revision/多产品共享策略演进、实际渠道及受控引用的发布校验，完成与acquire
  A0/A1集成验收。
- [ ] 费用：创单已冻结独立INIT order_fee和原总额规则；继续付清确认、退款累计基数与退费调整。
- [ ] payment：受理/查询及门禁检查已编译，完成真实MySQL/JWT/HTTP和并发验收；继续 acquire 金额占用与 accept 调用、敏感输入准备、绑定路由、额度预占/执行许可、Y2/渠道尝试、封禁关闭及可信结果。
- [ ] payment退款子域：独立refund_request/plan/refund_order、FIFO固定分配/原付款可退预占，不更新订单生命周期和支付金额。
- [ ] limitcenter：激活/原子预占/查询源码已补，完成窄范围构建及真实MySQL并发/JWT验收；执行许可/校验/显式过期源码已补；继续实现payment
  Y2/可信事实、安全换attempt、确认/释放、过期扫描、退款恢复、父级共享规则及历史记录。
- [ ] cashier/SDK：原单会话签发/重放、摘要能力认证和终态只读页面源码已补，完成窄范围构建及真实JWT/HTTP/MySQL验收；继续手动金额/尾款校验、每笔动作幂等、PENDING续付、完整渠道交互及会话撤销管理。
- [ ] 支付宝/微信/银联及直连/服务商目标接入，默认CNY；按实际开通产品补齐渠道/沙箱/银联机构接口材料。
- [ ] PaymentSucceeded/OrderFullyPaid及关闭/退款独立通知，签名/语义ACK/重试/版本幂等；realtime组合订单、付款、退款视图。
- [ ] 默认放行风控接口：渠道提交前咨询、结果落库后异步通知；详细外部协议后置。
- [ ] 后置reconciliation：独立文件/交易快照/逐笔核对/差错复核，不参与交易提交或直接改资金表。
- [ ] 如需多人长期使用Headlamp，接入OIDC/HTTPS；当前为本机短期token。
