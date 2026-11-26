# Product Center 产品中心 · 启动装配架构

## 职责与运行边界

可执行应用将领域与应用层、Web Controller、运行时持久化和框架组件组合成一个进程。[`ProductApplication`](src/main/java/io/github/loadup/productcenter/ProductApplication.java) 是启动边界，数据库和服务配置从本模块 [application.yml](src/main/resources/application.yml) 注入。

Flyway 从 `classpath:db/migration/pay/productcenter` 加载服务自己的迁移，与运行连接共用固定 schema。配置 Merchant Center 与 LimitCenter HTTP 客户端，确保 Task Worker 能处理策略发布。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
