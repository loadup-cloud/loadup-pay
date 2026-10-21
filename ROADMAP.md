# Pay remaining work

v1.4已将D01—D10收敛为可实施设计。2026-10-21完成P0/P1源码及六应用脚本、JAR/镜像构建、OrbStack启动与健康验收，见[实施说明](IMPLEMENTATION.md)
。client/domain契约已补齐；15类37个单测源码尚未编译或执行。复用框架Money/JSON诊断；测试RPC及协议模块已删除，业务RPC按P2实际用例接入。P2首批已新增customer授权/资格的app、持久化和HTTP源码，详情见[customer说明](customer/README.md)
；本批4类22个新单测源码未编译/执行，数据库/JWT业务验收尚待完成。product持久准入、条款编译与HTTP首批源码也已补齐，见[product说明](product/README.md)
；新增6类33个测试方法源码尚未编译/执行。limitcenter激活/原子预占/查询及执行许可/校验/显式过期源码已补齐，两批新增10类55个测试方法源码未编译或执行，见[limitcenter说明](limitcenter/README.md)
。product初始额度发布/Outbox/ACK/失败重放源码已补，另新增7类31个测试方法源码未编译或执行。以下保留尚未完成的执行与验收工作。

- [ ] 实现业务proto/HTTP适配、应用编排、数据库唯一约束/原子事务、事件和任务；补齐相应单测与业务集成验收。
- [ ] acquire：INIT/PENDING/PAID/CLOSING/CLOSED；固定总额/手动多笔付款、原子金额占用、CLOSE/KEEP_PENDING、终态写命令禁入。
- [ ] customer：执行新增模块的窄范围构建与单测；真实MySQL验收幂等/回滚、租户隔离、撤销后重授和资格并发；验证签名JWT与HTTP响应。主档维护入口继续按框架商户能力接入。
- [ ] 
  product：执行准入编排/持久化/HTTP及条款编译器的窄范围构建、单测与真实MySQL/JWT验收；初始额度策略可靠发布/ACK/重放源码已补；继续多revision/多产品共享策略演进、实际渠道及受控引用的发布校验，再接acquire
  A0/A1。
- [ ] 费用：ORDER_AMOUNT基数一订单一次计算/确认、独立order_fee；明确实现基线中的付清确认与退费调整。
- [ ] payment退款子域：独立refund_request/plan/refund_order、FIFO固定分配/原付款可退预占，不更新订单生命周期和支付金额。
- [ ] limitcenter：激活/原子预占/查询源码已补，完成窄范围构建及真实MySQL并发/JWT验收；执行许可/校验/显式过期源码已补；继续实现payment
  Y2/可信事实、安全换attempt、确认/释放、过期扫描、退款恢复、父级共享规则及历史记录。
- [ ] cashier/SDK：原单会话续发、手动金额/尾款校验、每笔动作幂等、PENDING续付和终态只读。
- [ ] 支付宝/微信/银联及直连/服务商目标接入，默认CNY；按实际开通产品补齐渠道/沙箱/银联机构接口材料。
- [ ] PaymentSucceeded/OrderFullyPaid及关闭/退款独立通知，签名/语义ACK/重试/版本幂等；realtime组合订单、付款、退款视图。
- [ ] 默认放行风控接口：渠道提交前咨询、结果落库后异步通知；详细外部协议后置。
- [ ] 后置reconciliation：独立文件/交易快照/逐笔核对/差错复核，不参与交易提交或直接改资金表。
- [ ] 如需多人长期使用Headlamp，接入OIDC/HTTPS；当前为本机短期token。
