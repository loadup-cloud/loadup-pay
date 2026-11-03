# LoadUp Pay Roadmap

## 已批准：Kubernetes 原生部署与 HTTP 全面迁移

决策日期：2026-11-02。状态：目标已确认，实施及运行验收待完成。

本路线图是后续完整迁移的目标，取代此前“默认统一依赖 Spring Cloud Alibaba/Nacos”的方案。
此前试接入的 Alibaba 默认装配已撤回。七应用 44/44 个旧内部操作已完成 HTTP 源码迁移；
内部 Protobuf/Wire/Dubbo 生成与运行依赖、nacos profile 已清理，Framework 可选 binder 保留。
逐操作与验收状态见 [HTTP 迁移清单](HTTP_MIGRATION.md)。2026-11-03 用户反馈本地编译无问题，测试验证暂缓；
此反馈不等于测试或真实环境验收通过，当前默认仍为平台线程。配置发布、网关及执行模型按下列阶段收口。
Spring Boot 保持 4.1.x，Java 保持 25。Framework 的配套工作见
[Framework Roadmap](../loadup-framework/ROADMAP.md)。

### 目标架构

| 能力 | 默认方案 | 按需扩展及边界 |
| --- | --- | --- |
| 注册与发现 | Kubernetes Service / DNS，普通 ClusterIP Service | 不默认部署独立注册中心，也不为 DNS 调用强制引入 Spring Cloud Kubernetes |
| 普通配置 | 版本化 ConfigMap，随滚动发布生效 | Secret 保存敏感配置；ConfigMap 或挂载文件更新不等于 Spring Bean 自动刷新 |
| 动态配置 | 默认不启用 | 确有动态推送、灰度配置需求时再评估 Nacos，明确刷新、审计与回退机制 |
| 平台入口 | Gateway API，默认实现为 Envoy Gateway | 云平台已有实现可在能力、兼容性验证后替换；不能只创建 API 资源而缺少控制器 |
| 业务入口 | 服务自身的 MVC API | 只有 BFF、业务聚合或复杂 Java 过滤需求时再增加 Spring Cloud Gateway，并划清两层职责 |
| 应用间调用 | Spring HTTP Service Clients，基于 RestClient 的声明式 HTTP 接口 | Pay 现有内部调用完整迁出 Dubbo；Framework 的 Dubbo Triple binder 保留为明确需求下的可选能力 |
| 跨语言或流式通信 | 不作为当前默认 | 有明确契约或流式需求时再评估 Protobuf / gRPC |
| 执行模型 | Spring MVC；虚拟线程在压测通过后作为默认启用 | 连接池、超时、并发隔离和下游容量仍需独立约束 |

服务目标按提供方应用配置基础地址，不按 RPC 方法逐个配置 URL。例如当前清单的同 namespace
地址为 `http://payment:8080`，跨 namespace 可使用
`http://payment.loadup-pay.svc.cluster.local:8080`；部署时以实际 Service 名、namespace、集群域和
Service 端口为准。普通服务器和本地开发通过基础地址覆盖配置接入，不在业务代码写 K8s 域名。

### 阶段 1：迁移清单与契约冻结

- [x] 盘点七个应用的内部调用、HTTP/Triple 提供方、Protobuf/mapper、身份传播、错误码和调用超时，建立 [逐接口迁移清单](HTTP_MIGRATION.md)（44/44 个操作已有源码契约）。
- [x] 为每个操作定义 HTTP 方法、路径、DTO、状态码、业务错误码及版本策略，明确金额精度、时间、枚举及可空字段的序列化。
- [ ] 保留现有租户来源、权限与受众约束、SERVICE token 轮换、幂等键、事务边界和未知结果处理；HTTP 内部入口不得信任调用方自报身份。
- [x] 更新 AGENTS、ARCHITECTURE 与首批模块文档中的 Dubbo-only 约束；区分源码与运行验收状态。

验收：每个旧内部操作都有明确的 HTTP 替代契约及行为验收项；不以移除注解或编译通过代替业务等价证明。

### 阶段 2：Framework HTTP 客户端基础能力

- [x] 在现有 HTTP 组件接入标准 HttpServiceProxyFactory + RestClientAdapter，共享命名连接池、生命周期与 Boot 观测；首批沿用 MVC/Resource Server 与业务租户上下文（运行待验收）。
- [ ] 按目标服务分组注册接口代理，统一地址、超时、连接池、TLS、令牌、追踪、错误映射与敏感信息保护；内部 MVC JSON 入站大小限制源码已补，分组装配及边界行为仍待运行验收。
- [ ] 明确重试由哪一层负责：支付、退款等写请求默认不自动重试，网关与客户端不得叠加隐式重试。
- [ ] 通过一个有代表性的跨服务调用验证鉴权、租户隔离、超时、错误映射和幂等；完成后将同一方式应用于全部调用。

验收：可用服务级地址配置完成调用，支持 K8s DNS 和本地地址覆盖；框架不把 Nacos 或 K8s API 客户端作为必需运行依赖。

### 阶段 3：七个应用完整迁移与默认依赖收敛

- [ ] 按调用依赖成组迁移七应用，逐项关闭阶段 1 清单。当前 44/44 操作已迁移源码，运行验收仍待完成。
- [ ] 每批同步更新调用方、提供方、测试与文档，保持业务语义；若需要混合版本窗口，先明确其兼容与回退方案，不默认引入长期双栈或自动 HTTP/RPC 回退。
- [x] 移除 Pay 对 Dubbo、内部 Protobuf 生成、专用 Wire mapper、Triple 过滤器及 RPC 发现配置的默认依赖，确认无剩余调用后清理对应启动和构建配置。
- [x] 移除七个应用对 Alibaba/Nacos 的默认装配及启动强依赖；Nacos 只在明确选用的集成方案中出现。
- [ ] 收敛依赖后验证应用在无 Nacos、ZooKeeper、Dubbo 注册中心和 Dubbo Kubernetes 扩展的环境中启动并完成内部调用。

验收：Pay 的内部调用已全部使用声明式 HTTP；无遗留 Triple 入口、直连 URL、必需 Nacos 配置或未使用的协议生成任务。
Framework 的可选 binder 不因 Pay 迁移而无条件删除。

### 阶段 4：K8s 配置、服务与发布机制

- [x] 统一普通 Service 的 HTTP 端口及调用地址；不再以 Headless DNS 直连作为 Pay 的内部调用默认机制。
- [x] 为 ConfigMap 选择版本化命名或内容校验值进入 Pod template 的明确方案，让配置版本变更触发滚动发布，并保留可回退的镜像/配置组合。
- [x] 配置源按 dev/test/prod 直接维护，删除模板生成流程；一键入口按内容计算不可变快照版本，敏感文件不入库。
- [x] 增加固定 RSA 公钥离线验签与开发令牌离线签发源码；同环境共享验签公钥，服务身份/受众/权限继续隔离。
- [ ] 构建并发布包含离线公钥支持的新 framework/业务镜像，验证后退役旧本地 JWKS 服务；源码完成不代表已迁移运行环境。
- [x] 普通配置与 Secret 分离；说明环境变量注入、挂载文件及 Spring Bean 的生效时机，默认通过重启生效，不承诺自动热刷新。
- [ ] 发布清单、资源渲染、readiness、优雅停机和 NetworkPolicy 同步迁移到 HTTP；清理已不再需要的 Triple 端口、Headless Service、发现 RBAC 和 Pod annotation 写权限。
- [ ] 验证多副本调用、连接复用下的流量分布、扩缩容、滚动更新期间的请求排空，以及版本化配置回退。

源码进度：显式 CONFIG_VERSION/SECRET_VERSION 绑定不可变环境快照，可变服务凭证独立挂载；发布前元数据预检并归档镜像/配置引用。
HTTP readiness、滚动策略与排空预算已补；HTTP NetworkPolicy 与网关资源已补，真实隔离、滚动/轮换/回退仍待验收。见 [配置与发布](deploy/CONFIGURATION.md)。

验收：服务发现由 K8s 托管，配置生效方式可预测、可追溯、可回退；HTTP 健康检查不代替真实业务调用验证。

### 阶段 5：Gateway API + Envoy Gateway

- [x] 确定与集群版本兼容的 Gateway API CRD 和 Envoy Gateway 版本，建立控制器的安装、升级、权限和回退方式。
- [x] 提供 GatewayClass、Gateway、HTTPRoute，以及 TLS 证书、域名、跨 namespace 引用需要的授权资源。
- [x] 明确入口 TLS、路由、请求大小、超时、访问日志和限流策略；所用 Envoy 扩展策略及其可移植性单独记录。
- [ ] 内部服务继续验证可信身份、租户及业务权限，不能仅凭网关注入的未受保护 Header 信任请求；保留审计和追踪关联。
- [ ] 验证支付通知等外部入口的签名原文、路径与头部透传，覆盖异常响应、大报文、网关更新及证书轮换。
- [x] Spring Cloud Gateway 当前不启用；如后续出现明确的 BFF/聚合/Java 业务过滤需求再加入，不重复负责平台入口治理。

源码进度：固定 Envoy Gateway v1.9.3 / Gateway API v1.6.1，提供原生 Helm 入口配置、TLS/窄范围 ReferenceGrant、显式公开路由和 Envoy 扩展策略。
回调原文/验签头不重写，移除 Envoy 重试/超时控制头；每路由本地限流、请求缓存和日志范围已定义。见 [网关说明](deploy/gateway/README.md) 与 [HTTP 网络策略](deploy/NETWORK_POLICY.md)。
2026-11-03 只读复核：orbstack 的 kubectl 已为 1.37.2，服务端为 1.36.5，先前版本差距已解决；Gateway API 与 EnvoyProxy CRD 已存在。
随后用户授权 Gateway 部署：Envoy Gateway v1.9.3 控制器 1/1、代理 2/2 就绪，Gateway 已 Programmed，全部 9 项策略 Accepted。
9 条路由均 Accepted，其中 reconciliation 因缺少业务 Service 而 ResolvedRefs=False；其他路由引用已解析。TLS 自签名证书校验与 HTTP 308 已验证。
acquire 旧镜像的 /checkout 经网关与直连均为 404；本次未更新业务镜像，六应用就绪不等于七应用迁移验收通过，NetworkPolicy 仍未部署。
仅执行平台就绪与只读连通性检查，单测及金融业务验收继续暂缓；当前环境快照见 [最终验收](deploy/ACCEPTANCE.md)。

后续用户授权的本地发布已完成：七应用 release-001 / cfg-20261103-r2 / sec-20261103 全部 1/1 就绪，
全部网关路由引用已解析，/checkout 与 /checkout.js 为 200；RustFS 使用已部署凭证的容器内签名 HEAD 为 200，七个入站 NetworkPolicy 已部署。
完整端到端、CNI 隔离、证书/凭证轮换、发布回退和容量验收仍待完成，详见 [本地发布结果](deploy/LOCAL_DEVELOPMENT.md#本次发布结果)。

验收：Envoy Gateway 是默认可部署的 Gateway API 实现，关键入口通过真实调用验证；写请求没有新增自动重试。

### 阶段 6：MVC 虚拟线程与最终验收

- [ ] 在 MVC 路径评估 `spring.threads.virtual.enabled=true`，检查阻塞客户端、上下文/安全身份传播、定时任务及自建执行器的实际行为。
- [ ] 对照平台线程基线压测吞吐、P95/P99、错误率、CPU/内存、数据库/HTTP 连接池等待及下游并发；设定通过阈值后再默认启用。
- [ ] 保留业务并发隔离、连接池上限、超时和背压；不把虚拟线程数量视为可用数据库连接或下游容量。
- [ ] 完成七个应用的真实端到端验收：付款、退款、限额、通知、查询、账单核对和未知结果恢复；覆盖幂等重放、故障及发布回退。
- [ ] 完成依赖、部署资源、文档和 AGENTS 的最终一致性检查；所有遗留默认依赖与临时迁移配置均有明确处理结果。

源码进度：七应用默认平台线程，显式 virtual-threads profile 使用原生 Boot 开关；两种模式均启用原生任务上下文传播。
真实嵌入式 MVC 线程/隔离、两类 applicationTaskExecutor 的 ScopedValue/Micrometer 传播和调度串行性回归源码已补，未执行。
HTTP 池原生状态与 lease 获取计时采集、刷新及关闭生命周期的回归源码已补；获取计时排除 TCP/TLS/HTTP 耗时，包含连接可用性校验。
压测门槛、采集项与七服务端到端证据见 [最终验收](deploy/ACCEPTANCE.md)；真实抓取、池饱和与 A/B 压测仍未执行。

完成条件：上述阶段全部验收，默认生产路径为 K8s Service DNS + HTTP Service Clients +
版本化配置 + Gateway API / Envoy Gateway；虚拟线程经过压测启用，Nacos 与 Dubbo 仅作为明确需求下的可选方案。
本路线图不授权直接部署、修改生产配置或执行金融业务请求；构建和运行验证沿用项目既有约定。

### 参考

- [Kubernetes Service DNS](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [ConfigMap 的生效与更新](https://kubernetes.io/docs/concepts/configuration/configmap/)
- [Gateway API](https://kubernetes.io/docs/concepts/services-networking/gateway/)
- [Envoy Gateway](https://gateway.envoyproxy.io/)
- [Spring HTTP Service Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [OpenFeign 的演进建议](https://docs.spring.io/spring-cloud-openfeign/reference/index.html)
- [Spring Boot 虚拟线程](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
