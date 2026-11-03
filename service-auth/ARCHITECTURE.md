# 服务凭证获取设计

ServiceAccessTokens 是业务传输层的单一入口，输入为已有的逻辑租户、调用方与目标 audience。配置建立这些身份组合到标准 ClientRegistration 的映射；未知组合在发请求前拒绝。

缓存、client_credentials 请求和有效期判断由 Spring AuthorizedClientServiceOAuth2AuthorizedClientManager/ClientCredentialsOAuth2AuthorizedClientProvider 管理，使用独立的应用 principal，支持任务线程。每条注册的锁避免同时续取；不同注册独立缓存，不从当前用户 SecurityContext 推导缓存身份。

RestClient 只连接受控 token endpoint，设置连接/读取超时，禁止重定向，默认要求 HTTPS。OAuth 失败转成固定诊断，不传播响应正文、密码或令牌。业务重试仍由原有可靠工作流决定，不因 401 自动重放金融请求。

客户端不替代资源端验签；商户/用户委托不经过本入口。多租户注册配置也不负责数据库 schema 路由。
