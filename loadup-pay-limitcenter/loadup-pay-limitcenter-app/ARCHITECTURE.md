# LimitCenter 额度 · 应用编排架构

## 职责与运行边界

[`LimitReservationService`](src/main/java/io/github/loadup/limitcenter/app/service/LimitReservationService.java)、[`LimitExecutionService`](src/main/java/io/github/loadup/limitcenter/app/service/LimitExecutionService.java) 组合领域规则和 Gateway。分步处理预占、执行门禁和结算。初次预占冻结原请求特征，门禁与过期争用同一规则下的锁，结算按可信付款事实推进。 HTTP 报文与具体 JDBC 适配分别由外层模块处理。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
