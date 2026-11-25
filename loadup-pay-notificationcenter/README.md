# Notification Center

独立通知应用，提供按 Service Code 的多渠道持久化受理与个人站内信。职责、事务与限制见 [ARCHITECTURE](ARCHITECTURE.md)。

## 接入与配置

单模块启动入口位于 `loadup-pay-notificationcenter/src/main`，HTTP 默认 8080，readiness 检查数据库。配置以 [application.yml](src/main/resources/application.yml) 为准。

| 配置 | 用途 |
|---|---|
| `NOTIFICATIONCENTER_JDBC_URL/USERNAME/PASSWORD` | 独立 PostgreSQL，不与交易服务共享表 |
| `NOTIFICATIONCENTER_JWT_ISSUER_URI` 与 JWKS/公钥配置 | 验证签名；audience 默认 notificationcenter |
| `DEPLOYMENT_CODE` / `DEPLOYMENT_DATABASE_SCHEMA` | 本部署固定标识与 schema，不由 JWT tenant_id 选择 |

启用 Resource Server，数据库使用单租户模式。调用客户端由 oauth2-server 数据库管理授权；内部 submit 不发布公网 Gateway 路由。Modules Notification 装配 Gotone 引擎、站内渠道、JDBC 路由存储与邮件适配器。邮件复用系统的 `JavaMailSender`，按 serviceCode 投递时由渠道配置选中。

## 多渠道提交

通过 `POST /api/internal/v1/notifications/dispatch` 按 serviceCode 提交，SERVICE 凭证需要
`SCOPE_notification:submit`。请求字段包括 `requestKey`、`serviceCode`、`title`、`body` 和
`recipientsByChannel`，后者按 `IN_APP`、`EMAIL`、`SMS`、`PUSH` 分别传入地址数组。管理页使用
`/api/notifications/routes` 配置和 `/api/notifications/publish` 受理。邮件使用系统发件器，短信和推送使用部署提供的 Provider。

## 站内提交

`POST /api/internal/v1/notifications/submit` 要求 SERVICE、subject=service_id 和 `SCOPE_notification:submit:in-app`：

```json
{
  "requestKey": "contract:123:activated:v1",
  "sceneCode": "CONTRACT_ACTIVATED",
  "recipients": ["user-123"],
  "channel": "IN_APP",
  "title": "合约已生效",
  "body": "你的合约已生效，可查看详情。",
  "actionUrl": "/contracts/123",
  "expiresAt": null
}
```

返回 SuccessResponse<NotificationAcceptedDTO>；同 source/requestKey 同内容返回原 ID/时间且 duplicate=true，改内容冲突。source 来自认证服务身份。受理成功不等于送达或已读。

## 用户接口

`POST /notifications/{list,unread-count,read,read-all,archive}` 要求有效用户身份；publish 仅超级管理员使用。来源应用不得以 publish 代替内部 submit。

## 限制与待验收

受理接口返回持久化结果，Task 负责按渠道投递。来源业务接线、数据库事务与幂等、Task 重试/过期/耗尽和权限隔离仍需运行验收，完整清单见架构文档。

阶段 1B：NotificationCenter 不再建立租户过滤器；JWT 凭证守卫保持 order=0。WebMVC 的 DeploymentCodeFilter 在安全链前校验部署 code，通知中心不读取 JWT tenant_id 或请求头选择 schema。
