# 首次安全输入准备与关闭竞态

状态：2026-10-28。当前完成原付款截止时间后的缺失输入收敛、输入发布与关闭串行化源码及测试源码；没有运行编译、测试、DDL 或真实
RPC/KMS/渠道。

## 原付款意图始终不变

acquire A2 已将原 allocation、paymentId、金额、渠道/方式、原 payableUntil 与请求摘要一起持久化。原 inputKey 等于
actionKey；原始付款码、渠道身份或 H5 场景不进入 acquire 数据库。恢复不能重新预占、创建另一笔 payment、更换渠道或生成当前请求的
IP/场景。

InputLookup 只读原不可变范围。查到输入时继续附加原 opaque 引用，沿用原期限，即使输入已经过期也不重建。已有的 InputExpire
仍负责“原输入已过期但尚未受理”的条件关闭；已受理付款沿原权威结果收敛。

## 始终查不到输入的收敛

| 原输入查找结果 | 条件                                | 后续行为                                                         |
|----------------|-------------------------------------|------------------------------------------------------------------|
| found          | 任意时间                            | 保留原引用，沿原输入到期/原受理流程                              |
| missing        | 当前绝对时间早于冻结 payableUntil   | INPUT_REQUIRED；保留原 HELD，不调用 prepare 或 accept            |
| missing        | 当前时间等于或晚于冻结 payableUntil | 调用已有 PaymentClosureClient.close，等待原服务持久门禁/关闭回执 |
| 错误或无效响应 | 任意时间                            | 传播失败/拒绝回包，不能视作 missing 或释放预占                   |

截止来源为 A2 冻结的原 payableUntil，不使用当前订单延期后的新时间，不增加猜测的输入超时常量。调用关闭在本地事务外，复用已有
provider-owned Dubbo Triple 和 acquire SERVICE 的 close 权限；没有新内部 HTTP、RPC 协议或令牌签发操作。

关闭请求身份固定：requestKey=`input-preparation-expiry/` + allocationId、reason=`INPUT_PREPARATION_EXPIRED`
、fenceEpoch=1。重复恢复始终使用原 key 和原付款，不新生成关闭身份。

只有返回原 order/payment、足够 fenceEpoch、fenced=true、payment=null 且有非空未收款证据，才能向原 A3 返回缺失受理的关闭证明。A3
继续记录原 NOT_ACCEPTED receipt 并一次性释放占用；恢复服务自身不直接修改资金预占或订单状态。

如果在途受理已先提交，close 返回真实 payment。恢复核验原目标/金额/生命周期后改走现有权威结果 refresh，不生成“未受理”证明、不另行
accept。关闭失败、回复丢失或缺少证据时原 HELD 保留；后续原请求重试可读回已提交回执。

## 防止迟到输入在关闭后发布

PaymentInputService 仍在本地数据库事务外执行指纹计算与 KMS 加密。加密完成后，通过 PaymentInputPublication 使用原 parent
gate 串行化最终发布：

1. 在本地写事务锁定原 order 的 parent gate；不存在时同事务创建 OPEN parent，重复键按已有 writeLocal 整体回滚后有限重试。
2. 在 parent 锁下再次查原 payment 的唯一输入。并发赢家已写入时只做精确幂等重放，不覆盖引用、期限、摘要或密文。
3. 没有原输入时，要求 parent OPEN，再锁原 child gate。FENCED 明确拒绝；已有受理的 inputless payment 不能事后补换输入；无
   payment 的 OPEN child 仍按原损坏状态拒绝。
4. 再检查原输入有效期，插入唯一 INIT 输入并与 parent 创建共同提交。

不会因为“输入已准备”创建 child gate。原系统的 OPEN child 与 payment 同事务存在；缺失 payment 的 child
必须继续具备原关闭回执，不能放宽为任意孤立 gate 都能证明未收款。

关闭、受理和最终输入发布都以原 parent 为锁序起点，因此关闭若先提交，迟到加密结果不能落库；输入若先提交，后续关闭仍可设置原
child 围栏，迟到的 accept 不能越过。已存在输入在关闭后的精确 prepare/lookup 重放只返回旧元数据，不续期或产生新密文。

父门禁创建只在输入发布本地事务中进行，失败会与输入一起回滚。整个锁区间没有 KMS、渠道或跨服务 IO，也不产生执行许可/付款提交权。

## 已补测试源码与边界

- PaymentInputPublicationTest：只创建 parent 的锁顺序、父/子围栏、OPEN 孤立 child、已受理 inputless payment、原服务身份与事务门禁。
- PaymentInputServiceTest：KMS 已完成但发布前关闭不落密文、围栏后的精确元数据重放，以及原指纹轮换/原期限测试调用同步。
- PaymentInitiationServiceTest：截止前保留 HELD、恰好原截止时幂等 close、失败/无证明拒绝、受理赢得竞争不构造缺失付款证明。
- FinancialPersistenceIT：真实 MySQL 中父围栏在受控加密等待时提交，迟到输入无数据库行/child；两首次准备并发最终返回一个原引用，仍不产生孤立
  child。加密和可信 actor 是测试替身，未替换业务存储。
- AcquiringFinancialPersistenceIT：同步现有 PaymentClosureClient 装配，避免将新编排依赖留给测试上下文缺省。

所有测试源码未编译或执行。真实 KMS/JWT/Dubbo Triple、多实例锁与输入保留清理仍需验收；不能将这些源码断言当成已通过的
SQL/并发证明。首次准备时的原始场景/IP 变化如何由客户端保持幂等、完整 cashier JSAPI 授权等原目标仍继续。
