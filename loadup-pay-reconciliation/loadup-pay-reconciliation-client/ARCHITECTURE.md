# Reconciliation HTTP contracts

client.http.ReconciliationHttpService 定义 POST /api/internal/v1/reconciliation/batches/{create,attach,start,retry,task,detail}。
复用原四个 Command，task/detail 使用 commons IdQuery。响应是 SuccessResponse<ReconciliationBatchDTO/BatchTaskDTO> 或 FailureResponse。
ReconciliationInternalController 委托既有 ReconciliationFacade，不改变账户目录、SERVICE/租户授权、任务、封存输入或事务。
原运营 HTTP 入口及 differences 分页保留；当前仓库无该声明式接口的消费者，受控集成仍需独立迁移验收。

ReconciliationContracts 与 Input/Batch/Task 构造器保留原 Wire 验证的必填、计数溢出、合法日期和状态/输入/run/任务关联。
INIT 不伪造运行/结果，MATCHED 必须零差错。JSON 严格整数、枚举和显式 version presence；金额不经 double。
client 不再包含 proto、Wire mapper、生成任务或 Dubbo 装配。

提供方完整 JWT 验证，身份来自签名 claims，普通 Header/账户自报 hash 不是授权依据。
错误、发布与未执行验收见 [迁移清单](../../HTTP_MIGRATION.md)。
