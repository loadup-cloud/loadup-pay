# Notification Center

Pay 通用通知中心，复用 Framework Notification module。系统设计与实施状态见 [ARCHITECTURE](ARCHITECTURE.md)。

当前批次组装已有站内信能力：`POST /notifications/list`、`unread-count`、`read`、`read-all`、`archive`；
`publish` 沿用 Framework 的超级管理员授权。个人身份要求认证主体提供有效 userId。
服务间使用 `POST /api/internal/v1/notifications/submit`，当前仅支持 IN_APP；请求与目标、Outbox 事件同事务受理，后台异步落入收件箱。
同 source/requestKey 重复内容返回原受理结果，修改内容返回冲突。详情和 JSON 示例见设计第 13 节。
需要 SERVICE 身份、subject = service_id、`SCOPE_notification:submit:in-app` 及配置租户匹配。
第三方渠道投递和来源业务应用接入尚待后续实现，不应将 publish 接口作为服务到服务的提交通道。

## 配置与运行前提

- 准备独立 PostgreSQL 数据库和默认 schema，不与 payment/acquire 共享表。
- 设置 `NOTIFICATIONCENTER_JDBC_URL`、`NOTIFICATIONCENTER_JDBC_USERNAME`、`NOTIFICATIONCENTER_JDBC_PASSWORD`。
- 设置 `NOTIFICATIONCENTER_JWT_ISSUER_URI` 及 JWK/公钥配置，audience 默认 `notificationcenter`。
- 设置 `NOTIFICATIONCENTER_TENANT_ID`（默认 `__default__`）；当前仅服务该租户，其他 tenant_id 的令牌被拒绝。
- 数据库组件尚无动态 schema 路由；当前使用一个默认 schema，不支持同实例承接多个租户。
- 启动时要求 Resource Server 开启、数据库使用单租户模式，避免将未实现的动态路由当作隔离能力。
- 默认 HTTP 8080，readiness 包含数据库；已作为内部服务加入发布脚本和数据库初始化，尚未加入网关或 AuthServer 授权清单。

## Gotone 渠道投递

Notification Center 单独装配 Gotone engine 与 JDBC store。Gotone 的服务/渠道配置及发送尝试记录保存在
Notification Center 自己的数据库及当前部署的默认 schema 中，与 Notification 表使用同一数据源和事务管理器；不跨库读取或写入。
其他 Pay 应用不安装 `loadup-components-gotone-store-jdbc`，也不共享这份存储。Gotone engine 本身没有数据库依赖，存储通过可选 SPI 接入。

`RecordHandler` 记录的是发送尝试日志，不能替代 Notification 的可靠业务状态及投递状态。当前尚未配置任何真实渠道，故不会进行真实渠道投递。

依赖版本由 Pay BOM 与 Framework BOM 管理。使用方需要先在本地准备相应 Framework 制品。
本批次未构建、未启动、未执行数据库迁移或真实发送。
