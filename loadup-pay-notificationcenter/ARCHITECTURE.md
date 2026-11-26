# NotificationCenter 架构

NotificationCenter 在同一个 PostgreSQL schema 内运行 Gotone 和 Inbox。Gotone 按已发布的 Service Code 对目标拆分，并在同一事务保存受理、投递和 Task。SMTP、短信、FCM 与 Webhook 在后台尝试；`IN_APP` 通过 `InboxReceiver` 幂等写入收件箱，与 Gotone 的投递状态更新共用本地事务。用户凭证仅允许读取自己的收件箱，平台管理与服务间提交通过不同授权进入。

支付商户回调由 Acquire 管理，支付渠道回调由 Pay Payment Channel 适配。Gotone 管理业务通知。

[Gotone 架构](../../loadup-modules/loadup-modules-gotone/ARCHITECTURE.md) · [接入](README.md)
