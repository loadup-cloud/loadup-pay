# Product Center 产品中心 · 领域模型架构

## 职责与运行边界

[`OrderAdmission`](src/main/java/io/github/loadup/productcenter/domain/model/OrderAdmission.java)、[`CommercialPolicyParser`](src/main/java/io/github/loadup/productcenter/domain/policy/CommercialPolicyParser.java) 维护核心领域状态。领域解析商业规则与冻结条款，校验策略和目录版本。准入事实保存来源标识与摘要，供收单侧核对。 数据库访问由 Gateway 端口隔离，HTTP 和 JDBC 实现位于外层。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
