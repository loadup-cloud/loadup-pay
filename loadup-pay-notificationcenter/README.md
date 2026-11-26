# LoadUp Pay NotificationCenter

NotificationCenter 装配 Gotone 和 Inbox 模块，提供按 Service Code 的多渠道异步投递及 `/api/inbox` 用户收件箱。Maven 依赖 `loadup-modules-gotone-web` 和 `loadup-modules-inbox-web`，消费 Pay BOM 中引入的 Modules BOM。生产部署通过本应用的私有服务地址接入。

Flyway 读取 `classpath:db/migration/pay/notificationcenter` 的基础表及 `classpath:db/migration/modules/gotone`、`classpath:db/migration/modules/inbox` 的业务表。脚本只创建表和索引；管理员需配置发送账号并发布服务编码。当前宿主使用 PostgreSQL；Gotone 建表 DDL 已在 MySQL 8.0、H2 2.4 验证，但其他引擎的完整接入尚未验证。邮件适配器随宿主引入，供应商凭证及端点按部署配置。

以 SERVICE JWT 调用 `POST /api/internal/v1/gotone/submit`，使用 `gotone:submit` scope。向 `GET /api/internal/v1/gotone/requests/{id}` 提交 `gotone:status` scope 可查询同来源的数据。配置与投递示例见 [Gotone 使用说明](../../loadup-modules/loadup-modules-gotone/README.md)。
