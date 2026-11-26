# Reconciliation 对账 · 领域模型架构

## 职责与运行边界

[`ReconciliationBatch`](src/main/java/io/github/loadup/reconciliation/domain/model/ReconciliationBatch.java)、[`ReconciliationMatcher`](src/main/java/io/github/loadup/reconciliation/domain/model/ReconciliationMatcher.java) 维护核心领域状态。批次保存原封存输入和比较身份。匹配器基于封存账本、渠道行生成可重复的差错与汇总。复核是独立业务记录。 数据库访问由 Gateway 端口隔离，HTTP 和 JDBC 实现位于外层。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
