# 对账差错独立复核（P1）

更新：2026-10-28。当前为源码与测试源码，未运行构建、测试、数据库迁移或真实身份/存储验收。

## 业务与身份边界

复核在 reconciliation 内独立记录运营结论，绑定原差错、原 runId、原结果 hash 与原完整匹配键 hash。
它不更新原账单、规范化行、差错、批次 MATCHED/DIFFERENCES 或付款/退款/费用/额度记录。
批次有差错，即使行政结论获批，原差错和原差错数仍然存在。

现有入站契约仅接受已验证 SERVICE/sub=service_id；本版 maker/checker 按两个不同 SERVICE 身份执行。
提交方和审批方都需要当前账户目录 service-ids 授权及各自 review 权限，不接受请求自报 operator、tenant、service 或权限。
这不是两名自然人的审批证明；自然人审批须后续接入可验证的人员身份和授权链，不能用备注里的姓名代替。

与原批次/导入只能原 caller 操作的契约并存：review 是显式授权的账户复核入口，可由独立审批服务访问原账户差错。
它不授予该服务修改原批次/导入或访问其他账户的权限。例如目录 service-ids 包含 pay-ops 与 pay-review-checker，
分别授予前者 review:propose、后者 review:read/decide；Token 发行与真实权限配置未执行。

## 提案、审批与终态

- 原差错存在且批次 `DIFFERENCES`时，可查询虚拟 INIT/version0；尚无 review 表行。
  首个提案把 head/version1、不可变 proposal 和不可变 event 一起写入。虚拟 INIT 不产生只读写入。
- 提案结论：CONFIRMED_DIFFERENCE、TIMING_DIFFERENCE、INPUT_DEFECT、NEEDS_SOURCE_VERIFICATION。
  结论是有审计的运营判断，不是渠道事实；INPUT_DEFECT 必须附有实际私有 File 证据。
  备注为 1–1024 字符的非空单行文字，原文参与幂等摘要，诊断输出隐藏。
- INIT 或 REJECTED 可以提交新提案，版本加一；PROPOSED 不允许被另一提案覆盖。
- 审批必须明确原 proposalId 与 expectedVersion；审批 service 必须不同于该提案的 proposerServiceId。
  APPROVED 为复核终态；REJECTED 保留旧提案/回执，可在当前版本上提出新提案。
- 状态顺序：INIT0 → PROPOSED1 → APPROVED2 或 REJECTED2 → PROPOSED3 → ...。
  PROPOSED 的版本为正奇数，APPROVED/REJECTED 为正偶数。审批不能回退到先前提案。
- NEEDS_SOURCE_VERIFICATION 的获批只确认“需要可信源端查证”的结论，批准本身不触发金融修正或补退款；后续显式查证请求可以调用源服务只读
  RPC。
  显式请求/Outbox、源端独立查询及观察留档源码已补，见 [查证设计](RECONCILIATION-VERIFICATION.md)；后续受控金融处置仍待设计。

## Client API 与运营 HTTP

| API                                                        | 请求/结果                                                                                                     |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| uploadEvidence / POST /api/reconciliation/reviews/evidence | multipart 私有证据，返回实际 File id/size/SHA256；不认定官方来源                                              |
| propose / POST /api/reconciliation/reviews/propose         | batchId、differenceId、requestKey、expectedVersion、conclusion、note、可选 evidenceFileId；返回原命令事件回执 |
| decide / POST /api/reconciliation/reviews/decide           | batchId、differenceId、proposalId、requestKey、expectedVersion、明确 approve 布尔值和 note；返回原审批回执    |
| detail / POST /api/reconciliation/reviews/detail           | batchId/differenceId；原差错、原结果引用、当前 head/proposal/最后事件                                         |
| history / POST /api/reconciliation/reviews/history         | batchId/differenceId/page/size；追加事件按 version 升序，默认 1/20、最大 100                                  |

独立权限 `pay:reconciliation:review:upload/propose/decide/read`；身份由现有已验证入站适配器提供，所有实际账户由服务端原批次决定。
这是运营入口，没有新增内部 HTTP 客户端或跨服务写调用。普通 DTO/领域/DO 映射复用 Spring MapStruct，金额沿用已有 Money。
复核新增 DTO 不传资金操作命令或支付状态。

## 幂等、文件和数据库

每条写命令按 tenant/callerServiceId/requestKey 唯一，摘要覆盖操作、原 batch/difference、expectedVersion、原提案或结论、
备注和 evidenceFileId；使用 UTF-8 长度帧，null 与空值不同。提案/审批共享该命令键空间，改参或换操作返回幂等冲突。

重放先核对当前账户授权、原差错引用和摘要，返回第一次命令的事件快照。原 PROPOSED1 的回包丢失后，
即使当前已经 REJECTED2/PROPOSED3，重放仍返回原事件/version1；当前状态通过 detail 读取。
独立事件表保存原 proposalId、操作、调用服务、版本、发生时间与审批备注；不从当前 head 伪造旧 ACK。

证据通过现有 FileResource 上传，propose 在事务外 inspect 实际归属/size/SHA256；写事务只核对元数据并附着
`reconciliation-review:<proposalId>` 的永久引用。审批/查询核对原提案归属和原引用，保留所有旧提案证据。
用户上传的文件不会冒充渠道已验签来源。原始备注与 storageId 使用 DiagnosticHidden，不进入诊断日志；受权运营 DTO 可以读取备注。
上传后未附着文件的清理、复核资料保留期和受控导出仍待单独完善。

三张表使用同一 DataSource：

- reconciliation_review：每个 difference 一行 head，原引用不可变；存 current proposal、last event 与 CAS version。
- reconciliation_review_proposal：不可变提案/结论/备注/原 File 快照/可信提交服务与本地业务时间。
- reconciliation_review_event：不可变命令回执与审计，tenant/caller/requestKey、tenant/difference/version 唯一。

锁序为原 batch → review head；读取原 run/差错/双侧来源行属于本服务本地查询，不跨服务或读取金融库。
永久文件引用、提案、回执、head CAS 在本地事务中一起提交；拒绝结果摘要或原匹配键变化。
存储端重演 head.propose/decide 验证合法转换后写入，不允许绕过领域直接改状态。没有更新旧 proposal/event 或删除历史的 API。
纯详情和分页使用只读 RR，历史 count/page 与 head.version 一致且序列连续；head 缺失但已有事件时不能伪装 INIT。

迁移源码 `V20261028010000__reconciliation_review.sql`，使用 BaseDO 和注解处理生成的 Mapper/TableDef；未执行 DDL。
业务时间来自注入的共享 Clock（默认系统时区），协议没有强制 UTC 或全局时区设置。

## 当前证据与剩余工作

已有领域、应用、存储映射及 MySQL IT 测试源码：独立审批/过期版本/错提案、驳回后重提、原命令重放、权限与账户撤销、
证据引用失败、并发同请求、实际 review/File 元数据回滚、审批不改原结果/差错。
IT 使用真实 MySQL 元数据，受信测试 actor 与私有对象字节传输为受控替身；不能据此声称真实 JWT/DFS/人员审批已通过。
所有新增源码均未编译或执行。实际 JWT/权限/对象存储/多实例竞争及受控金融处置仍待完成；支付宝来源/解析等 P1 其他工作继续保留。
