# Acquire 收单 · 启动装配架构

## 职责与运行边界

可执行应用将领域与应用层、Web Controller、运行时持久化和框架组件组合成一个进程。[`AcquireApplication`](src/main/java/io/github/loadup/acquire/AcquireApplication.java) 是启动边界，数据库和服务配置从本模块 [application.yml](src/main/resources/application.yml) 注入。

Flyway 从 `classpath:db/migration/pay/acquire` 加载服务自己的迁移，与运行连接共用固定 schema。需对齐 productcenter、payment 和 realtime 目标。签发与校验托管收银台会话所需的密钥由宿主提供。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
