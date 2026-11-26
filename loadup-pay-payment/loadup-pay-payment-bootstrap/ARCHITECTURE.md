# Payment 付款 · 启动装配架构

## 职责与运行边界

可执行应用将领域与应用层、Web Controller、运行时持久化和框架组件组合成一个进程。[`PaymentApplication`](src/main/java/io/github/loadup/payment/PaymentApplication.java) 是启动边界，数据库和服务配置从本模块 [application.yml](src/main/resources/application.yml) 注入。

Flyway 从 `classpath:db/migration/pay/payment` 加载服务自己的迁移，与运行连接共用固定 schema。按业务启用渠道 profile，配置渠道身份材料与依赖方地址。服务使用自身数据库 schema 中的业务与任务表。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
