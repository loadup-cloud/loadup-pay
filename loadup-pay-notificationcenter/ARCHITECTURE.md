# Notification Center 架构

接入和请求示例见 [README](README.md)，通用实现见 [Notification 模块](../../loadup-modules/loadup-modules-notification/ARCHITECTURE.md)。

## 职责与数据归属

| 层次 | 当前职责 |
|---|---|
| notificationcenter | 单模块 Pay 应用，组装认证、独立 PostgreSQL、Notification、Gotone 与后台处理器 |
| Notification client/core/web | 通知提交、请求与目标幂等、站内信及已读/归档生命周期 |
| Task | 与请求同事务登记事件，领取、租约、重试与失败管理 |
| Gotone engine/store-jdbc | 可选外部渠道发送引擎、渠道配置和尝试日志；当前未装配真实供应商 binder |

请求、目标、站内信、Task 与 Gotone 存储使用本应用数据源。Notification/Gotone 保留各自表所有权，不通过跨表 SQL 耦合。应用显式选择框架业务迁移及自身组件快照，不扫描共享迁移根目录。

当前一套部署服务一个租户、使用固定 schema；身份来自已验证 JWT，部署标识取自本地固定配置。用户 Inbox 是消息收件箱，Task Inbox 是消费去重凭证，二者不互相替代。

## 受理与站内信投递

1. 内部 submit 只接受 SERVICE、sub=service_id 和 `SCOPE_notification:submit:in-app`。source 从签名身份取得，body 不能指定来源或租户。
2. 当前仅 IN_APP；以 source+requestKey 去重。接收人去空白、去重和排序，摘要绑定渲染后内容；同内容回原 notificationId/acceptedAt，不同内容冲突。
3. 请求、目标和仅含 deliveryId 的 Task 事件同事务提交；受理 ACK 只证明持久化，不表示用户收到或已读。
4. 后台锁定目标，在同一事务写站内信及 DELIVERED；过期目标记 EXPIRED。站内信阅读/归档独立，不因重试重置。
5. 重复投递由目标/站内信唯一约束处理。目标仍为 PENDING 时，关联 Task 可能已 FAILED，排查须同时检查 event_id；人工重放需验证原期限及状态。

来源应用若要求业务提交后可靠通知，应在自身本地事务写通知意图，事务外调用中心；不能用内存线程池或跨库事务代替。来源重试保持同 key，不重新生成业务通知。

## 用户与支付边界

用户查询和更新只操作认证主体的个人消息；publish 沿用通用模块超级管理员授权，不能作为服务提交入口。merchantId 不能代替个人 userId。

acquire 的商户支付协议回调有独立签名、期限、ACK 和领域状态，继续由其 Task 驱动；不迁入通用通知中心，也不被 Gotone Webhook 替代。普通通知失败不改变支付事实。

## 能力限制

- 不接受 SMS/Email/Push 或混合渠道，拒绝码为 NOTIFICATION_UNSUPPORTED_CHANNEL；Gotone 配置表和日志不等于可靠外部投递已完成。
- 不支持模板参数、业务关联查询、发送状态查询和细粒度场景白名单；来源业务的实际接线按场景单独验收。
- 外部渠道需另行设计目标/尝试状态、供应商幂等、UNKNOWN 查证、期限、人工重放及隐私保留策略；租约不能保证外部 exactly-once，不允许不确定请求直接换供应商重发。
- Gotone RecordHandler 只是尝试日志，不能代替 Notification 业务请求/目标状态。敏感内容、地址和凭证不得进入普通日志。

## 待验收

PostgreSQL 初始化与事务回滚、受理回复丢失/重复/冲突、过期目标、进程崩溃与租约并发、Task 耗尽及人工重放、JWT scope/服务身份、个人消息越权拒绝，以及来源到站内信的真实链路。供应商真实发送不在当前能力范围。

阶段 1B：NotificationCenter 不再建立租户过滤器；JWT 凭证守卫保持 order=0。WebMVC 的 DeploymentCodeFilter 在安全链前校验部署 code，通知中心不读取 JWT tenant_id 或请求头选择 schema。
