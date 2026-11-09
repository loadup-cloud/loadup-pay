# AuthServer 设计

应用仅装配协议、持久化和 Pay 的服务授权语义。协议使用 Spring Security Authorization Server；RSA 签名及公钥发布由框架 SAS binder 管理，私钥操作委托 KmsTemplate，应用不加载或导出 RSA 私钥。

ServiceClientCatalog 是经过审查的环境授权输入。ServiceClientRegistry 使用 PostgreSQL 事务级 advisory lock 串行同步至标准 RegisteredClientRepository，密码只持久化 BCrypt。删除/禁用的条目失去原凭证；ServiceTokenCustomizer 在签发时重新读取当前授权，限制 grant、scope 和身份。客户端额外请求参数不改变租户和 audience。

注册信息与已签发授权使用 Spring 的 JDBC 存储实现，Flyway 维护官方 7.1 协议表。表结构遵循第三方 JDBC 契约，id 长度、协议时间列和序列化设置列按该契约保留，不套用业务 BaseDO。服务授权没有新增业务表 tenant_id 列，逻辑租户存于注册的 client settings。

HTTP 安全链仅允许标准 OAuth2 服务协议和基础健康探针，未实现用户登录、OIDC 或管理 API。应用没有启用框架通用协议安全链，使用专门的 service-only 链，保留框架提供的 KMS encoder/JWKS/settings。

资源端继续本地验签和业务授权；数据库中的授权记录不提供 JWT 的即时撤销。默认有效期 5 分钟。部署的动态 schema 隔离与租户注册尚未实现，不能从此授权服务推导出数据库隔离已完成。
