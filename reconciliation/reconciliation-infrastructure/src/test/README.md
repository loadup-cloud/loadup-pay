# Reconciliation 测试源码范围

当前包含受控账户目录、封存/批次/差错 MapStruct 映射和微信 ALL 格式解析测试源码。
上层 app 包含原请求幂等、双输入门禁、原运行登记、事务外比较、任务重放与结果持久化失败顺序；adapter/rpc 包含身份与协议边界。

本轮没有编译或运行任何测试。app 的计数型事务替身不能证明真实 SQL 回滚；没有用 MapStruct 往返代替 MySQL 外键/唯一键/锁竞争。
MySQL 输入封存/原生 Transfer 与业务同库/并发/租户 IT 源码已补；比较结果的差错/完成真实回滚及所有 IT 实际运行仍待完成，真实
JWT/HTTP/gRPC/File/Transfer/S3 链路仍待验收。

新增 ReconciliationImportPersistenceIT 源码使用真实 MySQL、File/Transfer facade、生成 Mapper 与应用服务；只为外部对象字节和可信
actor 提供测试适配。
覆盖业务登记后故障使任务/双引用/导入一起回滚、封存更新后故障使输入行/批次指针/逻辑结果一起回滚、原 FAILED
重试、永久坏尾部拒绝、并发原请求和跨租户读取拒绝。
检查业务长期引用在 native 临时引用释放后仍阻止文件删除。没有运行这些用例，也不将内存对象替身视为真实 S3/网络/租约恢复验收。

## 官方来源接线测试源码（2026-10-28）

新增 ReconciliationStatementStorageConverterTest 覆盖 INIT/SUPERSEDED 的缺失 receipt、READY 的私有文件/精确协议微秒映射与错误
presence；账户目录测试增加原绑定读取及 manual-only 账户拒绝官方下载。原 ReconciliationImportPersistenceIT 装配同步
OfficialImports/StatementGateway 与新账户接口，仍只证明其实际执行后覆盖到的路径。

app 新增来源请求/下载编排与官方 lineage 单测源码，覆盖原请求先登记、原绑定、File/Transfer 注册、原任务失败重试/丢
ACK、缺失账单、租约和权限。专用来源原子回滚、多 worker 条件提交、RPC/对象存储部分失败 MySQL IT 尚待补齐；当前没有运行任何测试或迁移。

## 独立复核测试源码

ReconciliationReviewStorageConverterTest 覆盖原引用/精确版本/nullable File 快照与私有诊断。ReconciliationReviewPersistenceIT
使用真实 MySQL review/File 元数据，受控 actor/私有对象字节替身，注入实际 commit 后失败，检查
head/proposal/event/永久引用同库回滚、独立审批、并发同请求原 ACK 与不改变原差错/结果。领域和 app 单测覆盖
INIT、过期版本/错提案、自审批拒绝、驳回后新提案、重放旧 ACK、权限/账户撤销和证据失败。

本轮未编译或执行任何新测试，也未启动容器、运行 DDL 或连接真实外部服务。源码覆盖不作为当前事务原子性或权限验收证明。

## 获批差错源端查询测试源码

ReconciliationVerificationStorageConverterTest 覆盖 INIT 缺失回执、覆盖缺口无隐式0、精确金额/协议微秒及原提示/实际流水分别保存。app/domain
新增原获批范围、请求/Outbox 先落、原回包关联、native attempt/ACK 丢失与不可变回执、原 deliveryId 重试与 canonical payload
测试源码。payment app/rpc 增加只读查询/授权、覆盖缺口、optional/int64/微秒与退款受理时间测试源码。

源端 MySQL 账户/多候选/退款 claim 一致性、查询索引、请求/Outbox/回执实际回滚和多 worker 的专用 IT 仍待补；本轮没有执行任何构建、测试、容器、DDL
或真实调用。

## 源端查询持久化与原生 Outbox IT 源码（2026-10-28）

新增 ReconciliationVerificationPersistenceIT，复用真实 MySQL、生成 Mapper、应用事务及原生
JdbcOutboxRepository/OutboxDispatcher；受控替身只用于可信 actor、对象字节及 payment 查询传输。测试上下文关闭后台轮询，由用例显式派发原生任务。

覆盖请求实际 INSERT 后故障使请求/事件一起回滚、回执实际 UPDATE 后故障使回执/版本回滚、原 FAILED 事件重试及审计、回执提交后
ACK 丢失不重复查询、并发原请求只登记一次、原生 claim 过期后新 worker 首次提交而旧查询/ACK 不覆盖、无源对象保留 nullable
覆盖缺口及跨租户拒绝。租约竞争通过更新本用例事件的过期时间协调，不靠 sleep。原差错、审批事件和批次结果保持断言一并加入。

这是测试源码进度，未编译或执行，也未启动容器、迁移或数据库操作。payment 源端 SQL 候选/原退款 claim 的专用 IT、真实
JWT/TLS/RPC/渠道和多实例验收仍未完成；不以本地 payment 替身代替那些证据。
