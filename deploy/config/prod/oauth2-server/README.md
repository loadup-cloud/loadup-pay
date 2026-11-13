# OAuth server 环境配置

OAuth 客户端由数据库管理。部署前通过受控的运维流程在数据库中创建并启用每个调用方的 RegisteredClient，配置 grant type、audience/scope 和权限。应用启动不会从配置文件导入或覆盖客户端；客户端记录必须先存在。

在本目录创建忽略提交的 `secret.env`，配置数据库密码和签名 KeyStore 密码：

```dotenv
AUTHSERVER_JDBC_PASSWORD=
KMS_KEYSTORE_PASSWORD=
```

各调用方自己的 `secret.env` 配置 `SERVICE_AUTH_CLIENT_SECRET`，需与数据库中客户端登记的密钥一致。密钥文件为本目录的 `oauth2-server.p12`，包含 `AUTHSERVER_SIGNING_KEY` 指定的 RSA alias。配置工具会将其写入单独的版本化文件 Secret，仅挂载给 OAuth server。
