# Reconciliation 对账 · 启动装配架构

## 职责与运行边界

可执行应用将领域与应用层、Web Controller、运行时持久化和框架组件组合成一个进程。[`ReconciliationApplication`](src/main/java/io/github/loadup/reconciliation/ReconciliationApplication.java) 是启动边界，数据库和服务配置从本模块 [application.yml](src/main/resources/application.yml) 注入。

Flyway 从 `classpath:db/migration/pay/reconciliation` 加载服务自己的迁移，与运行连接共用固定 schema。配置 Payment HTTP 目标、S3/文件能力、Transfer 扫描与 Task Worker。Flyway 另外纳入 Framework File 和 Transfer 目录。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
