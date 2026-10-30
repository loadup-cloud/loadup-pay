# payment 权威账务采集快照

状态：2026-10-27 P1 源码已补，未编译、生成验证、运行测试、执行 DDL、调用真实 RPC/渠道或部署。
此处 capture 是账务采集快照， **不是资金授权/capture**；交易入口仍按已确认的 DirectPay/Checkout 设计。

## 源端范围与证据

当前只实现 WECHAT / `wechat-trade-all-20260116`，实际收款账户 hash 与账单日构成范围。
支付宝仍待官方固定文件格式与日期字段落地；不会为未实现的 profile 返回空成功。
收款来自不可变成功 transaction claim；核对原付款/attempt、历史绑定、商户、原请求号、金额/CNY、成功时间、终态证据及 APPLIED 回执。
退款来自独立 refund_acceptance_evidence，以渠道 create_time 的首次已验证 acceptedAt 定账单日，核对原收款完整证据、子退款、金额/账户/原号和日期回执。
成功退款还需 terminal refund claim 与 APPLIED 终态回执；PROCESSING/UNKNOWN/FAILED 保留实际状态，不能伪装成功或删除。
收款金额与退款申请总额分开，退款不减少收款总额。

渠道日界为微信国内账单协议的 +08:00 午夜；转换到注册时冻结的系统 zone 后查询本地 DATETIME (6)。
不改 JVM/Clock/JDBC/K8s 全局时区。RPC Timestamp 保留绝对时刻，源内容摘要按 instant 规范化，接收端 offset 变化不会改变摘要。

一次源读取处于独立、只读 REPEATABLE_READ，默认10秒预算、10000行，分组读取最多256个关联身份；不取凭证/输入密文/合约
payload，不锁金融行或做网络 IO。
该预算通过 TransactionTemplate 配置；真实 JDBC 超时、查询计划和负载效果尚未运行验收。
RR 的起止时间是采集过程标记，不宣称是数据库全局提交水位；后续迟到事实不会回写原快照，可用新请求/新批次复核。

## 生命周期与覆盖阻断

Create 在本地 RC 事务中登记 INIT 元数据与原 native DelayedTask，原 caller/requestKey 唯一，摘要绑定原范围及系统 zone。
Worker 核对实际 RUNNING 任务、身份、attempt、原期限与有效租约，在 RR 内读源，事务外生成完整摘要，再用短 RC 锁采集头提交全行与唯一终态。
原生租约是至少一次执行； **同一 capture 首个有效提交结果固定**。并发/旧 worker 不能覆盖已提交结果；native token fencing
仍由框架负责 ACK。
任务从不修改资金、容量、订单、退款或原财务记录的 audit 字段。

| 逻辑状态         | 内容                                                    | 后续                            |
|------------------|---------------------------------------------------------|---------------------------------|
| INIT/version0    | 没有起止时间、计数、金额或 hash                         | 原任务可继续/失败重试           |
| SEALED/version1  | 全行、显式零或完整总计、原内容 hash                     | 只读重放，永不刷新/覆盖         |
| BLOCKED/version1 | 一个阻断码/主体样本，起止时间；没有可导出行、总计、hash | 查证后用新 requestKey 申请新 RR |

阻断条件：

- ACCOUNT_SCOPE_UNKNOWN：本租户不存在该实际账户的历史绑定。
- UNRESOLVED_COLLECTION：账单结束前已提交的 attempt，没有成功 claim 且没有持久未收款证据。
- REFUND_ACCEPTANCE_MISSING：账单结束前已提交退款缺渠道受理日期，不能以本地提交或退款成功时间替代。
- REFUND_ACCEPTANCE_CONFLICT：出现首次日期与后续事实冲突异常，不能静默选一个日期。
- RECORD_LIMIT / TOTAL_OVERFLOW：完整范围超过上限或整侧金额溢出，不能截断导出。

这些是保守覆盖阻断样本， **不是差错全列表，也不是资金失败证据**。
早期未决/缺日期记录可能阻断后续账期，当前不自动推断“必定没有入账”。可证明的未受理退款排除及受控差错查证仍须独立业务证据设计。

nativeStatus 与逻辑状态独立：例如 SEALED + FAILED 可能是保存后 ACK 丢失。
Retry 只条件重排原 FAILED 任务，核对 expectedTaskId，不换请求/任务、不刷新 SEALED/BLOCKED。
重复 Retry 遇到原任务已 PENDING/RUNNING/SUCCEEDED 返回当前状态，CANCELLED 明确拒绝。
任务级失败/预算超时保留 INIT，不能制造 BLOCKED 的确定覆盖事实或零行成功。

## 源端 client/gRPC

PaymentCaptureFacade 归 payment-client；payment-rpc 自有 `payment_reconciliation.proto`：

| 方法   | 入参                                                          | 结果/权限                             |
|--------|---------------------------------------------------------------|---------------------------------------|
| Create | requestKey/channel/accountScopeHash/billDate/statementProfile | PaymentCaptureDTO；capture:create     |
| Detail | IdQuery                                                       | PaymentCaptureDTO；capture:read       |
| Lines  | captureId/page/size                                           | 通用 PageDTO；仅 SEALED，capture:read |
| Retry  | captureId/expectedTaskId                                      | PaymentCaptureDTO；capture:retry      |

权限完整前缀 `pay:payment:reconciliation:`；service_id 必须等于配置的 reconciliation-service-id，租户来自已验证 SERVICE
JWT，原 caller 匹配。
Create 只允许已结束的渠道账单日。页码默认1/20、最多100；分页读取源封存行，不重复扫描财务表。
源端两表为 payment_reconciliation_capture /
payment_reconciliation_line；V20261027030000__payment_reconciliation_capture.sql 只作为待执行迁移源码。

Wire scalar optional、金额/版本/ordinal int64；严格缺字段、未知枚举/日期/profile、状态 presence、微秒 Timestamp 与页码/连续
ordinal/EOF shape。
集合通过 protobuf builder adders 写入。Spring MapStruct 与注入的 typed RpcOperation 使用 provider-owned contract，无静态
Mapper INSTANCE 或内部 HTTP 回退。

## 对账侧导入

运营 HTTP 与 ReconciliationLedgerFacade：

1. `POST /api/reconciliation/ledger/prepare`，batchId/requestKey。核对原 caller/当前账户授权，按原批次 scope 发 Create。
   源请求 key 对 tenant/caller/batch/requestKey 定长摘要，回复丢失可原请求重放；返回原 source capture/native 状态。
2. 原源任务处理后，再调用 `/import`，batchId/captureId。只接受同范围 SEALED，所有 RPC 均在本地事务外。
3. 每页100，核对每页原 total/page/size、连续 ordinal、账单日；短末页之后仍请求空 sentinel。
   完整总计、全源 hash 与读取末尾的原 capture 元数据再次核对，拒绝部分集合、变更范围/内容或溢出。
4. 生成 provider-owned `payment-ledger-capture-v1` 二进制档案：UTF-8
   字段用4字节有符号长度前缀（null=-1），完整源元数据、每行身份/状态/时间/金额/证据/版本以及总计。
   二进制原字节 SHA256 **正好等于源 contentHash**，不使用公开 DTO JSON 序列化结果充当源身份；默认16 MiB上限。
5. 事务外上传私有 FileResource。短本地事务再次核对当前账户、原 scope 和批次；长期文件引用、LEDGER manifest/全行与批次关联原子提交。
   原 sourceCaptureId 保留，artifactId 属于本次输入；规范化 hash 使用 reconciliation 自有格式，原/规范化 hash 分开。
6. 已挂同一原 capture 重放只查本地；其他 capture 冲突，不能替换。任一写失败不允许批次 READY，原 capture 可重试导入。

`/retry` 使用 batchId/captureId/expectedTaskId，只条件重试源端任务。运营权限为
`pay:reconciliation:ledger:prepare/import/retry`，原 caller 与当前账户目录仍必须允许。
当前准备、等待、挂入是显式步骤；没有自动轮询/导入或自动开始核对。随后使用既有 batch start，双侧独立来源才能 READY。
来源文件在上传与本地挂入之间失败可能成为无引用资源，后续按 File 政策处理；永久引用不跟随 native 任务历史自动清理。

## 装配与待验收

出站固定 reconciliation SERVICE，逐调用读取挂载 token，当前 payment transport 对应一个明确租户，其他租户拒绝；不转发运营者令牌。
实际接收方仍做签名/issuer/audience/token_use/权限校验，本地 token claims 检查只是路由预检。
`dns:///payment-headless:50051`、round_robin、默认每 RPC 3秒，完整分页抓取预算60秒，可配置。
预算在每次调用间检查，单次最后 RPC 可再消耗自己的 deadline；文件上传与最后本地事务有各自负责层超时，不宣称整个 HTTP 硬截止60秒。

源端配置与对账出站凭证见各模块 README；本轮未签发、读取真实密钥或部署凭证。
测试源码包含纯模型/协议/hash/归档上限、服务身份/幂等/native 重试、预算/隔离级别、完整分页/坏页/当前授权、
真实 MySQL 源查询、并发 RR、封存 SQL 写后统一回滚、退款日期缺失/跨日/状态/冲突；全部尚未运行。

P1 后续仍包括官方自动账单下载与来源校验、支付宝固定解析、差错复核/可信证据查证、自动化运营链；
支付异常/关单证据、服务商 H5、微信 V2 撤销/mTLS 和收银台 JSAPI 也没有因本切片完成而被标成已完成。
