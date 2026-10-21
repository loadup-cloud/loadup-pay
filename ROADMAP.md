# Pay remaining work

v1.4已将D01—D10收敛为可实施设计。2026-10-21完成P0/P1源码，见[实施说明](IMPLEMENTATION.md)：六应用初始化及client/domain契约已补齐，包括关单/延期、可信付款结果、顺序退款与容量、额度原桶恢复、授权撤销和完整投影快照。累计15类37个单测源码；未构建、测试或部署，容量等指标不展开。复用框架Money/JSON诊断；测试RPC及协议模块已删除，业务RPC按P2实际用例接入。以下保留尚未完成的执行与验收工作。
- [ ] 实现业务proto/HTTP适配、应用编排、数据库唯一约束/原子事务、事件和任务；构建、单测及启动验收。
- [ ] acquire：INIT/PENDING/PAID/CLOSING/CLOSED；固定总额/手动多笔付款、原子金额占用、CLOSE/KEEP_PENDING、终态写命令禁入。
- [ ] product/customer：新单权威准入、商户/子商户逐项授权；普通合约停止仍允许原INIT/PENDING续付，冻结原条款。
- [ ] 费用：ORDER_AMOUNT基数一订单一次计算/确认、独立order_fee；明确实现基线中的付清确认与退费调整。
- [ ] payment退款子域：独立refund_request/plan/refund_order、FIFO固定分配/原付款可退预占，不更新订单生命周期和支付金额。
- [ ] limitcenter：按每笔受理时间、PAYMENT_COUNT及日/周/月/终生桶原子预占/确认/释放，可配置恢复/父级规则且历史累计不清零。
- [ ] cashier/SDK：原单会话续发、手动金额/尾款校验、每笔动作幂等、PENDING续付和终态只读。
- [ ] 支付宝/微信/银联及直连/服务商目标接入，默认CNY；按实际开通产品补齐渠道/沙箱/银联机构接口材料。
- [ ] PaymentSucceeded/OrderFullyPaid及关闭/退款独立通知，签名/语义ACK/重试/版本幂等；realtime组合订单、付款、退款视图。
- [ ] 默认放行风控接口：渠道提交前咨询、结果落库后异步通知；详细外部协议后置。
- [ ] 后置reconciliation：独立文件/交易快照/逐笔核对/差错复核，不参与交易提交或直接改资金表。
- [ ] 如需多人长期使用Headlamp，接入OIDC/HTTPS；当前为本机短期token。
