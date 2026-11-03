# AuthServer 环境配置

手动维护 `../service-authorizations.json`：每条记录绑定一个调用方、逻辑租户、目标 audience 和 permissions。
这里的 tenantId 是逻辑身份，不是数据库 schema，不会给业务表添加 tenant_id。

在本目录创建忽略提交的 `secret.env`，配置：

```dotenv
AUTHSERVER_JDBC_PASSWORD=
KMS_KEYSTORE_PASSWORD=
ACQUIRE_OAUTH2_CLIENT_SECRET=
PAYMENT_OAUTH2_CLIENT_SECRET=
PRODUCT_OAUTH2_CLIENT_SECRET=
LIMITCENTER_OAUTH2_CLIENT_SECRET=
RECONCILIATION_OAUTH2_CLIENT_SECRET=
```

各调用方自己的 `secret.env` 只配置本应用 `SERVICE_AUTH_CLIENT_SECRET`，与 AuthServer 对应值一致。
密钥文件为本目录的 `authserver.p12`，包含 `AUTHSERVER_SIGNING_KEY` 指定的 RSA alias。
配置工具会将其写入单独的版本化文件 Secret，仅挂载给 AuthServer。
本地可运行 `scripts/authserver.sh prepare-dev` 安全生成缺失项；已有文件和值不会覆盖。
