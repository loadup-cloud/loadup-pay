# LimitCenter 额度 · 领域模型架构

## 职责与运行边界

[`LimitReservation`](src/main/java/io/github/loadup/limitcenter/domain/model/LimitReservation.java)、[`LimitBucket`](src/main/java/io/github/loadup/limitcenter/domain/model/LimitBucket.java) 维护核心领域状态。预占和桶按原时间窗口与规则身份计量。原规则维度维持稳定，同一次预占命中的所有桶一起成功或回滚。 数据库访问由 Gateway 端口隔离，HTTP 和 JDBC 实现位于外层。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
