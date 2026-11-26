# LimitCenter 额度 · 启动装配架构

## 职责与运行边界

可执行应用将领域与应用层、Web Controller、运行时持久化和框架组件组合成一个进程。[`LimitCenterApplication`](src/main/java/io/github/loadup/limitcenter/LimitCenterApplication.java) 是启动边界，数据库和服务配置从本模块 [application.yml](src/main/resources/application.yml) 注入。

Flyway 从 `classpath:db/migration/pay/limitcenter` 加载服务自己的迁移，与运行连接共用固定 schema。配置本服务服务令牌、签发 HMAC 执行许可所需的版本化密钥 及到 Payment 的证据查询身份。

[本层接入](README.md) · [服务整体设计](../ARCHITECTURE.md)
