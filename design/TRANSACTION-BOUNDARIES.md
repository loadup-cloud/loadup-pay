# 本地事务与租户上下文

更新：2026-10-27，对应 review R5。当前完成源码、调用方与契约测试调整，没有运行验证。

## 命名与边界

| 方法              | 是否创建真实事务      | 隔离/只读                 | 闭包执行次数                                                      | 外层事务                     |
|-------------------|-----------------------|---------------------------|-------------------------------------------------------------------|------------------------------|
| withTenantContext | 否                    | 不改变调用方事务属性      | 一次                                                              | 保留原事务，不自动挂起或退出 |
| readSnapshot      | 是，REQUIRES_NEW      | REPEATABLE_READ、readOnly | 一次，不因数据库异常重试                                          | 已存在真实事务时拒绝         |
| writeLocal        | 是，每次 REQUIRES_NEW | READ_COMMITTED、可写      | 成功一次；仅重复键/TransientDataAccessException 最多总计 3 次尝试 | 已存在真实事务时拒绝         |

withTenantContext 绑定可信租户、调用闭包并按本模块转换异常，退出后恢复外层租户。它不是“无事务保证”，不能用它掩盖已存在的写事务。
远端调用仍须由业务入口确认当前没有真实事务；付款/退款原有前置检查保持。该方法不新建连接、事务、线程或重试。

readSnapshot 只供本地一致视图：禁止写入、FOR UPDATE 和远端 IO。读失败直接传播/按本模块原规则转换，不重放闭包。
writeLocal 的闭包必须是可重放的本地数据库工作；不包含 RPC、渠道提交、HTTP、KMS 或不可回滚的外部副作用。
只有前一次 TransactionTemplate 执行已经完全回滚退出后，才开始下一次事务。普通业务异常不在重试范围，不能因为失败就创建新金融身份或扩大权限。

## 当前实现范围

| 模块 helper                             | 租户上下文入口                                | 快照入口                       | 本地写入口                            |
|-----------------------------------------|-----------------------------------------------|--------------------------------|---------------------------------------|
| acquire / OrderCreationTransactions     | public withTenantContext                      | public readSnapshot            | public writeLocal                     |
| payment / PaymentAcceptanceTransactions | public withTenantContext                      | public readSnapshot            | public writeLocal                     |
| realtime / RealtimeTransactions         | private withTenantContext，仅内部装配事务     | public readSnapshot            | public writeLocal                     |
| product / PublicationTransactions       | public withTenantContext                      | 未增加，现有读取保持上下文语义 | public writeLocal                     |
| limitcenter / LimitTransactions         | public withTenantContext，使用可信 LimitActor | 未增加，现有读取保持上下文语义 | public writeLocal，保留原冲突映射参数 |

customer 没有对应独立 helper，本轮不为统一命名新增技术层。
realtime 原 read 是真实只读 RR，所以调用方直接改为 readSnapshot；不能改为上下文-only，否则会丢失投影分页/详情的一致性。
其余原 read 是租户上下文，所以改为 withTenantContext，不新增只读事务。
五个 helper 的原 write 改为 writeLocal；private scoped 也显式命名为 withTenantContext，不保留旧方法或兼容重载。
开发期同步修改生产/测试/真实 MySQL IT 调用方，不涉及公开业务 DTO、RPC、数据库格式或混合版本方案。

## 保持的业务语义

- acquire/payment 的运营详情、UNKNOWN 列表和采样仍在已有 readSnapshot，写重试仍是 read committed。
- 付款 Y2/退款准备继续先 writeLocal 提交，再在事务外调用原渠道，然后独立 writeLocal 应用结果；不把远端提交纳入本地重复键重放。
- acquire 订单、占用、费用/通知 Outbox 与原生任务仍共享原本地写事务，恢复代次和原提交权限不变。
- realtime 查询保留只读 RR，投影状态/receipt 仍在同一次本地写中应用，不使用新 helper 反向查询主交易服务。
- product/limitcenter 保留各自冲突/额度错误映射；没有提取 pay-common、BaseService 或共享跨库事务执行器。
- callback 结果约束保留：acquire/payment/product/limitcenter 的 writeLocal 仍要求非 null；realtime 的原读写允许
  null，不借重命名改变这项行为。

## 契约测试源码与验收

OrderCreationTransactionsTest / PaymentAcceptanceTransactionsTest
补充：真实事务标记、隔离级别、只读标记、每次回滚后再进入下一次、总计三次上限、普通业务异常不重试、上下文恢复及拒绝外层事务。
RealtimeTransactionsTest 独立覆盖 readSnapshot 的只读 RR/单次失败、local write 的 RC/有限重试和原 null 返回行为。
PublicationTransactionsTest / LimitTransactionsTest 独立覆盖上下文-only、回滚后重试、模块冲突/额度错误映射、原业务失败不重试、嵌套本地写拒绝。
已有付款/退款执行测试和三服务 MySQL IT 调用同步新名称，继续承担渠道事务外调用及实际持久化证明。

本轮没有执行 IDEA 检查、编译、测试、数据库或网络调用，也未以手动检查代替验证。
新增 helper 契约测试使用 Spring TransactionManager SPI 的受控替身，只描述标记/闭包/回滚调用次序，不证明 MySQL 隔离、锁、SQL
回滚或多实例行为；R1/R2 实测仍独立待办。
R5 完整运行验收待用户恢复验证，不能因重命名或测试源码存在而标为通过。
