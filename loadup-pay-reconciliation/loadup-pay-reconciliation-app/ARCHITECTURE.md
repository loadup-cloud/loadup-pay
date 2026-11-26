# Reconciliation 对账 · 应用编排架构

## 职责与运行边界

[`ReconciliationService`](src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationService.java)、[`ReconciliationExecutionService`](src/main/java/io/github/loadup/reconciliation/app/service/ReconciliationExecutionService.java) 组合领域规则和 Gateway。登记批次并固定原运行身份，执行时读取双侧封存输入，事务外进行确定性比较，再提交全部差错与完成摘要。 HTTP 报文与具体 JDBC 适配分别由外层模块处理。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
