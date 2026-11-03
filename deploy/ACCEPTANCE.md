# HTTP 迁移与虚拟线程验收

当前状态：44/44 内部操作、版本化发布、HTTP 入站策略及 Envoy Gateway 已有源码。
2026-11-03 用户反馈本地编译无问题，测试验证暂缓；助手未重新执行构建或单元测试。
随后用户授权部署 Gateway，控制器/代理/策略已部署并完成下述就绪及只读连通性检查；完整业务验收仍未完成，
后续源码变更仍须按实际版本重新核对编译范围，不把这条反馈当作所有后续版本的通过记录。
本文把源码检查与实际运行证据分开，不能用表中的测试名称或示例命令视为通过。Boot 4.1.x / Java 25 保持不变。

同日用户继续授权七应用发布：已执行 clean package（跳过测试）并构建七个 release-001 镜像，
以 cfg-20261103-r2 / sec-20261103 发布后七应用 1/1 就绪、Service 端点就绪；本地 JWT 信任源与 RustFS 已接入。
首次冷启动探针超时的失败候选保留，第二轮发布及有限只读检查见 [本地部署结果](LOCAL_DEVELOPMENT.md#本次发布结果)。完整业务验收仍未完成。

## 当前环境只读快照

2026-11-03 在用户授权 Gateway 部署后更新 orbstack 快照；仅核对平台就绪、TLS 与只读 GET，没有执行金融请求或完整业务测试。

| 项目 | 当前证据 | 仍未证明 |
| --- | --- | --- |
| 客户端/服务端 | kubectl v1.37.2 / Kubernetes v1.36.5+orb1，先前版本差距已解决 | 后续发布与业务正确性 |
| 应用 | 七应用 release-001 均 1/1 就绪，ConfigMap 为 cfg-20261103-r2，环境 Secret 为 sec-20261103；均有就绪 8080 Service 端点 | 真实内部调用、扩缩容、零停机滚动与业务恢复 |
| Gateway API | 复用现有 Gateway API v1.6.1/Envoy CRD；部署 Envoy Gateway v1.9.3，控制器 1/1、代理 2/2 就绪 | 升级、回退与长期稳定性 |
| 网关实例 | GatewayClass Accepted=True，Gateway Accepted/Programmed=True；8 个 BackendTrafficPolicy 与 1 个 ClientTrafficPolicy Accepted=True；9 个路由 Accepted/ResolvedRefs 均为 True | 业务调用、轮换、压力与故障验收 |
| 本地 TLS/入口 | pay.loadup.local，LoadBalancer 192.168.139.2；自签名证书校验通过，HTTP 308；公有证书在忽略目录，2026-12-03 到期；本机 hosts 与系统信任未修改 | DNS/浏览器接入与生产证书 |
| 只读转发 | 更新后 HTTPS /checkout、/checkout.js 均为 200；customer readiness 为 200；原 GET 内部 API/Actuator 检查为网关 404；抽查一个代理无配置更新 failure/rejected | 完整协议、授权矩阵与业务验证 |
| RustFS | 复用现有 Docker RustFS，开发 bucket 已创建；从 reconciliation 容器使用已部署凭证执行签名 HEAD 为 200 | 真实文件上传/下载、权限与故障恢复 |
| 网络策略 | 七个应用 NetworkPolicy 已部署 | CNI 实际隔离及完整允许/拒绝验证 |

这份快照只记录当前对象状态和上述有限检查，不替代完整测试或后续发布授权；已有副本 ready 不代表本轮迁移业务验收通过。
具体 release、证书与本机接入方式见 [网关部署结果](gateway/README.md#本地部署结果)。

## 线程配置

七应用基础配置显式设置 spring.threads.virtual.enabled=false。候选 application-virtual-threads.yml 只启用原生虚拟线程开关和 keep-alive，
需要显式激活；压测通过前不改变默认值。两个线程模式均配置 spring.task.execution.propagate-context=true，
由 Boot 原生 Micrometer 上下文传播与已有 LoadUpContextTaskDecorator 组合，不引入新的线程框架。

本地开发追加 virtual-threads 到原有 profiles，例如 local,virtual-threads；保留其他渠道/业务 profile。
环境变量、命令行等更高优先级来源仍可覆盖 profile，不能只看文件名判断实际线程模型。
K8s 也可在新 ConfigMap 快照中显式设置 SPRING_THREADS_VIRTUAL_ENABLED=true 与 SPRING_MAIN_KEEP_ALIVE=true，再按匹配版本滚动；
不得修改旧快照。回退使用保留的平台线程配置版本。

原生配置说明见 [Boot 虚拟线程](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
和 [任务执行/调度](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)。
keep-alive 防止仅剩 daemon 工作线程时 JVM 退出，不会扩容连接池。

## 静态评估与回归源码

| 范围 | 当前实现与回归入口 | 能证明什么、还缺什么 |
| --- | --- | --- |
| 同步 MVC | WebMVC 的 MvcThreadModelTest | 使用回环地址启动真实嵌入式 Servlet 容器，对两个线程模式检查 Thread.isVirtual、并发租户/主体隔离及失败后清理；尚未执行 |
| 业务元数据/观测 | Observability 的 LoadUpContextPropagationTest | 两种原生 applicationTaskExecutor，检查 ScopedValue 与 Micrometer ThreadLocal 同时捕获/恢复；尚未执行 |
| 扫描调度 | Local Scheduler 的 LocalSchedulerTest | 两种模式下实际调度唤醒、fixed-delay 不重叠；尚未执行，不等于持久化领取/租约验收 |
| HTTP 消费 | HttpServiceClientsTest 与各 HttpClientTest | 连接/池等待/读取上限、无重试、原令牌与本地错误映射已有回归源码；仍需真实 TLS/JWT/下游验证 |
| HTTP 池观测 | HttpPoolMetricsTest / HttpTemplateTest | 原生 lease 饱和超时、计时边界、刷新连续性、注册失败回收、scope 隔离和关闭清理已有回归源码；尚未执行 |
| JSON 数值契约 | Commons JSON 的 IntegerJsonContractTest | 金额/版本拒绝浮点截断、BigDecimal 精度和数字枚举拒绝；EnumFeature 使用 Jackson 3 API，尚未执行 |
| 原生回调 | ProtocolResponseTest 与 Payment 回调 Controller | 原生 ACK/状态/内容类型的表示层边界已有源码；真实渠道签名、原文与代理透传尚未验收 |
| 持久化/金融语义 | 各 infrastructure 的 PostgreSQL IT 与应用层测试 | 事务、receipt、Outbox、租约、未知结果等已有回归入口；本次未运行，不能由线程测试代替 |

MvcThreadModelTest 仅在测试类中创建合成身份，不读取业务配置、不连接业务数据库或渠道，不能证明 Resource Server JWT 验签或租户数据库隔离。
真实鉴权仍需验证签名、issuer、audience、有效期、token_use、SERVICE 身份及业务权限。后台任务应从持久记录重建可信身份，不依赖请求线程残留。

Local binder 的 fixed-delay 扫描继续串行执行各 scanner，虚拟线程不会自动并行化 Outbox/DelayedTask。
Boot 自动调度器与任务执行器可能随全局虚拟线程开关改变；自定义执行器不会因此统一替换。
业务元数据需要显式 wrap/decorate，认证上下文按原生安全集成及调用方明确授权处理；数据库事务不能跨线程继承。

## 开发者验证入口

按用户当前要求暂缓测试。以下仅保留后续恢复验证时的入口，未执行；由开发者选择受影响模块，始终 clean：

```bash
# 在 loadup-framework 根目录；仅选择本阶段相关模块及其依赖。
mvnd clean test \
  -pl loadup-components/web/loadup-components-webmvc,loadup-components/platform/loadup-components-observability,loadup-components/execution/loadup-components-scheduler/loadup-components-scheduler-binder-local \
  -am -Dmaven.test.skip=false -DskipTests=false -Dskip.unit.tests=false -Dtest=MvcThreadModelTest,ProtocolResponseTest,RequestBodyLimitAdviceTest,LoadUpContextPropagationTest,LocalSchedulerTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dskip.spotless=true -Dskip.spotbugs=true
```

该命令只覆盖线程与 MVC 边界。HTTP 组件的池观测、声明式调用、协议信封、地址策略与配置约束，按
[HTTP 组件的定向验证入口](../../loadup-framework/loadup-components/integration/loadup-components-http/README.md#能力矩阵) 单独选择该模块及其依赖；
该入口明确包含本轮新增 HttpPoolMetricsTest，不以旧的单个 HttpTemplateTest 覆盖全部能力。
JSON 的数值与枚举边界另有窄模块入口，同样待恢复测试时再执行：

```bash
mvnd clean test -pl loadup-commons/loadup-commons-json -am \
  -Dmaven.test.skip=false -DskipTests=false -Dskip.unit.tests=false -Dtest=IntegerJsonContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dskip.spotless=true -Dskip.spotbugs=true
```

这些命令均不覆盖全部七服务、业务协议或 PostgreSQL，业务迁移仍按 [逐操作清单](../HTTP_MIGRATION.md) 与各模块测试完成。
部署脚本回归位于 scripts/tests；网关语义用例需额外的 test-only PyYAML，依赖见该目录 requirements.txt。
它们不安装控制器、访问真实集群或执行金融请求，仍不能代替 CNI/CRD/流量验收。

## A/B 压测准备与记录

先在获准的隔离环境完成正确性和依赖装配，再做平台线程 A 与虚拟线程 B；使用相同镜像、JVM/堆、CPU/内存、Pod 数、数据库快照、连接池、
HTTP 基础地址/超时、网关限流及背景任务库存。只改变线程开关及必要的 keep-alive。记录配置/环境 Secret 版本与实际生效属性。
不要因虚拟线程更便宜而同时增加数据库池、HTTP 池、任务批次或渠道并发，那会使比较失效。

每组建议预热 5 分钟、稳定采样至少 15 分钟并重复 3 次；低负载、正常负载和受控饱和分开记录。
固定到达率、实际完成吞吐、超时和限流都要保留；只统计成功请求会隐藏积压。网关本地限额随代理副本变化，不是全局容量上限。
数据集、流量工具/版本、目标地址、请求分布、连接复用和重试策略必须相同。默认使用获准的只读查询与合成下游；
付款/退款/回调/关闭/复核等写入与真实渠道操作需要单独明确授权，不由本路线图或压测配置自动授权。

| 证据 | 采集与判读 |
| --- | --- |
| 线程模型 | 保留 JDK/Boot 版本、实际请求线程证明；普通 JVM 平台线程数量不能证明虚拟请求线程已经启用 |
| 吞吐、P95/P99 | 标准 HTTP server histogram 与同一客户端原始统计；同时统计业务失败，不把 HTTP 200 都当成功 |
| 资源 | CPU、RSS/堆、GC、分配、容器重启/OOM、在途与排队趋势；不能仅看平均值 |
| 数据库 | 连接池 active/idle/pending、获取等待和超时、事务时长/锁等待、数据库负载；实际 exporter 名称以目标版本为准 |
| HTTP 池 | loadup.http.pool.leased/pending/available/max/route.max 与 acquire 计时的 outcome；源码采集已补，真实抓取与饱和验证仍待执行。acquire 包含连接可用性校验，排除 TCP/TLS/HTTP 耗时；需要分位数时预先启用原生 histogram |
| 业务并发 | 现有渠道恢复 Bulkhead、任务领取/租约、扫描持续时间、Outbox 堆积、下游饱和与恢复 |
| 上下文/追踪 | 不同租户/主体并行、异常、异步边界和任务重建；验证审计/trace 关联，指标标签不得带租户、订单或 token |
| JDK 诊断 | 在受控环境保留 JFR/线程转储及分析结论；按 Java 25 实际行为判断，不因源码有 synchronized 就推断发生 pinning |

线程池大小参数在虚拟线程模式下不再表示并发上限。spring.task.execution.simple.concurrency-limit 等只约束相应原生任务执行器，
不能当成 MVC 请求并发阀门；server.tomcat.max-connections 也不是请求数或数据库容量。
现有 HTTP 有界池/阶段超时、数据库池、业务 Bulkhead 和金融围栏继续有效，前端、后台和外部渠道容量分别评估。
Java 25 的诊断方式参见 [JDK 虚拟线程说明](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)。

## 启用门槛

在正式采样前冻结门槛，不能测后放宽再宣称通过。以下是初始建议，业务 SLO 更严格时以其为准：

- 正确性：身份/租户串用、丢事件、重复金融效果、幂等或围栏破坏均为 0；发现一项立即停止推进。
- 对比：同等正常负载下完成吞吐至少为基线 95%，P95/P99 不高于基线 105%；业务错误、超时、限流不能增加。
- 容量：无 OOM/持续积压；连接获取满足既定超时，下游没有因增加在途而失稳；CPU/内存增长需在预先冻结预算内。
- 发布：两个及以上副本的分布、扩缩容、长请求排空、配置回退、凭证/证书轮换均有记录；单 Pod ready 不算通过。
- 收益：在目标 I/O 负载下有可重复收益；没有收益时保持平台线程，不以“配置能启动”作为默认启用依据。

以上数值是待实验前确认的工程门槛，不是已测出的 SLO 或通过结论。缺失池等待、真实错误或背景积压证据时，结果应为 INCOMPLETE。

## 七服务端到端与最终收口

| 链路 | 必须验证的事实 |
| --- | --- |
| customer/product | 原商户委托与 audience、资格拒绝/依赖失败区分、原合约/额度发布版本/hash 与幂等 |
| acquire/payment | 付款受理、输入过期/不存在、未知结果查证、原请求身份恢复、关单/围栏；超时不当成未收款 |
| limitcenter | 预占、执行凭证、消耗/释放、退款恢复、只读源证据；错误白名单与并发边界 |
| 退款/通知 | 原付款绑定、独立退款幂等、成功证据、原文验签、提交后 ACK、重投原事件与 receipt |
| realtime | 四类快照、显式版本 0、旧版本/重复/冲突 ACK、收款与退款独立事实、查询租户隔离 |
| reconciliation | 双侧完整输入、计数/金额/哈希、分页与字节完整性、原 run/task 重试、差错复核与只读查证不自动金融处置 |
| 平台 | 无独立注册中心启动、内部 DNS/HTTP、完整 JWT/TLS、CNI 隔离、网关无隐式重试、版本化配置与滚动回退 |

每项填写镜像/配置版本、环境、执行者、命令/用例、原始证据位置和结果；敏感工件保存到受限位置，不放入 Git。
使用 [验收记录模板](acceptance-report.template.md)，未执行保持 NOT_RUN。所有必需项完成之前，roadmap 继续保持未验收，默认虚拟线程关闭。
