# LoadUp Pay Roadmap

## 已批准：Kubernetes 原生部署与 HTTP 全面迁移

决策日期：2026-11-02。状态：目标已确认，实施及运行验收待完成。

本路线图是后续完整迁移的目标，取代此前“默认统一依赖 Spring Cloud Alibaba/Nacos”的方案。
此前试接入的 Alibaba 默认装配已撤回；当前源码仍使用 Dubbo Triple，默认直连、Nacos profile 可选。
记录目标不代表已完成 HTTP 迁移。
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

- [ ] 盘点七个应用的全部内部调用、HTTP/Triple 提供方、Protobuf/mapper、身份传播、错误码和调用超时，建立逐接口迁移清单。
- [ ] 为每个操作定义 HTTP 方法、路径、DTO、状态码、业务错误码及版本策略，明确金额精度、时间、枚举及可空字段的序列化。
- [ ] 保留现有租户来源、权限与受众约束、SERVICE token 轮换、幂等键、事务边界和未知结果处理；HTTP 内部入口不得信任调用方自报身份。
- [ ] 更新 AGENTS、ARCHITECTURE 和模块文档中的 Dubbo-only、Nacos 默认依赖等约束，使后续实现与本路线图一致；区分现状与目标。

验收：每个旧内部操作都有明确的 HTTP 替代契约及行为验收项；不以移除注解或编译通过代替业务等价证明。

### 阶段 2：Framework HTTP 客户端基础能力

- [ ] 基于现有 HTTP、Web MVC、Resource Server、上下文和可观测组件接入 HTTP Service Clients，复用已有客户端资源管理，避免重复建设连接池。
- [ ] 按目标服务分组注册接口代理，统一基础地址、连接/读取超时、连接池、TLS、令牌和追踪传播、错误映射及敏感信息保护。
- [ ] 明确重试由哪一层负责：支付、退款等写请求默认不自动重试，网关与客户端不得叠加隐式重试。
- [ ] 通过一个有代表性的跨服务调用验证鉴权、租户隔离、超时、错误映射和幂等；完成后将同一方式应用于全部调用。

验收：可用服务级地址配置完成调用，支持 K8s DNS 和本地地址覆盖；框架不把 Nacos 或 K8s API 客户端作为必需运行依赖。

### 阶段 3：七个应用完整迁移与默认依赖收敛

- [ ] 按调用依赖成组迁移 customer、product、limitcenter、payment、acquire、realtime、reconciliation 的消费端与提供端，逐项关闭阶段 1 的清单。
- [ ] 每批同步更新调用方、提供方、测试与文档，保持业务语义；若需要混合版本窗口，先明确其兼容与回退方案，不默认引入长期双栈或自动 HTTP/RPC 回退。
- [ ] 移除 Pay 对 Dubbo、内部 Protobuf 生成、专用 Wire mapper、Triple 过滤器及 RPC 发现配置的默认依赖，确认无剩余调用后清理对应启动和构建配置。
- [ ] 移除七个应用对 Alibaba/Nacos 的默认装配及启动强依赖；Nacos 只在明确选用的集成方案中出现。
- [ ] 收敛依赖后验证应用在无 Nacos、ZooKeeper、Dubbo 注册中心和 Dubbo Kubernetes 扩展的环境中启动并完成内部调用。

验收：Pay 的内部调用已全部使用声明式 HTTP；无遗留 Triple 入口、直连 URL、必需 Nacos 配置或未使用的协议生成任务。
Framework 的可选 binder 不因 Pay 迁移而无条件删除。

### 阶段 4：K8s 配置、服务与发布机制

- [ ] 统一普通 Service 的 HTTP 端口及调用地址；不再以 Headless DNS 直连作为 Pay 的内部调用默认机制。
- [ ] 为 ConfigMap 选择版本化命名或内容校验值进入 Pod template 的明确方案，让配置版本变更触发滚动发布，并保留可回退的镜像/配置组合。
- [ ] 普通配置与 Secret 分离；说明环境变量注入、挂载文件及 Spring Bean 的生效时机，默认通过重启生效，不承诺自动热刷新。
- [ ] 发布清单、资源渲染、readiness、优雅停机和 NetworkPolicy 同步迁移到 HTTP；清理已不再需要的 Triple 端口、Headless Service、发现 RBAC 和 Pod annotation 写权限。
- [ ] 验证多副本调用、连接复用下的流量分布、扩缩容、滚动更新期间的请求排空，以及版本化配置回退。

验收：服务发现由 K8s 托管，配置生效方式可预测、可追溯、可回退；HTTP 健康检查不代替真实业务调用验证。

### 阶段 5：Gateway API + Envoy Gateway

- [ ] 确定与集群版本兼容的 Gateway API CRD 和 Envoy Gateway 版本，建立控制器的安装、升级、权限和回退方式。
- [ ] 提供 GatewayClass、Gateway、HTTPRoute，以及 TLS 证书、域名、跨 namespace 引用需要的授权资源。
- [ ] 明确入口 TLS、路由、请求大小、超时、访问日志和限流策略；所用 Envoy 扩展策略及其可移植性单独记录。
- [ ] 内部服务继续验证可信身份、租户及业务权限，不能仅凭网关注入的未受保护 Header 信任请求；保留审计和追踪关联。
- [ ] 验证支付通知等外部入口的签名原文、路径与头部透传，覆盖异常响应、大报文、网关更新及证书轮换。
- [ ] 如后续增加 Spring Cloud Gateway，仅承载明确的 BFF/聚合/Java 业务过滤能力，不重复负责平台入口治理。

验收：Envoy Gateway 是默认可部署的 Gateway API 实现，关键入口通过真实调用验证；写请求没有新增自动重试。

### 阶段 6：MVC 虚拟线程与最终验收

- [ ] 在 MVC 路径评估 `spring.threads.virtual.enabled=true`，检查阻塞客户端、上下文/安全身份传播、定时任务及自建执行器的实际行为。
- [ ] 对照平台线程基线压测吞吐、P95/P99、错误率、CPU/内存、数据库/HTTP 连接池等待及下游并发；设定通过阈值后再默认启用。
- [ ] 保留业务并发隔离、连接池上限、超时和背压；不把虚拟线程数量视为可用数据库连接或下游容量。
- [ ] 完成七个应用的真实端到端验收：付款、退款、限额、通知、查询、账单核对和未知结果恢复；覆盖幂等重放、故障及发布回退。
- [ ] 完成依赖、部署资源、文档和 AGENTS 的最终一致性检查；所有遗留默认依赖与临时迁移配置均有明确处理结果。

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
