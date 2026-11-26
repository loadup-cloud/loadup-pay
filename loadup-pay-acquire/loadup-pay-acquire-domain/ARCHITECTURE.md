# Acquire 收单 · 领域模型架构

## 职责与运行边界

[`AcquireOrder`](src/main/java/io/github/loadup/acquire/domain/model/AcquireOrder.java)、[`OrderPaymentAllocation`](src/main/java/io/github/loadup/acquire/domain/model/OrderPaymentAllocation.java) 维护核心领域状态。订单和付款占用归收单侧管理。已付总额与在途占用之和受订单金额限制，终态由权威付款事实驱动。 数据库访问由 Gateway 端口隔离，HTTP 和 JDBC 实现位于外层。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
