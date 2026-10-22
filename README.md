# LoadUp Pay

LoadUp 收单支付平台：面向商户及子商户的外部渠道收单、付款与退款。

当前为六应用初始化与契约层：Java 25、Spring Boot 4.1.1，通过 LoadUp BOM 管理依赖。独立于框架集成测试启动器。启动装配暂不依赖数据库、Redis
或注册中心。

- acquire、payment、customer、product、limitcenter、realtime：独立Bootstrap，HTTP 8080与Actuator基础健康入口。
- 每个服务已拆为client/domain/bootstrap；业务Facade仍是接口，尚未提供下单、支付、退款的应用服务或Controller。

当前仅提供应用骨架与业务契约，不创建支付、不记账。测试用Ping RPC及协议模块已删除；业务gRPC在实际用例落地时接入。

P0/P1源码结果与优先级见 [第一阶段实施说明](IMPLEMENTATION.md)。Money复用`loadup-commons-dto`的
`io.github.loadup.commons.money.Money`；CNY/非负约束由收单领域校验，金额仍为整数分。业务数据对象的toString统一调用
`ToStringAsJson.reflectionToString`，敏感字段使用`DiagnosticHidden`。当前无RPC协议模块或gRPC运行依赖，Java业务client保持与传输无关。

P1已补齐聚合生命周期、关单证据、顺序退款/容量、额度原桶恢复、授权撤销和完整投影事件，保留15类37个单测源码。六应用已完成JAR/镜像构建及本地Kubernetes启动验收；本轮未编译或执行单元测试，业务功能仍为契约层。

## 架构设计

详细设计入口：[COLA DDD与关键流程](DETAILED-DESIGN.md)、[数据库与DDL草案](design/DATABASE.md)、[Client API与通知协议](design/CLIENT-API.md)、[合约参数](design/CONTRACT-SCHEMA.md)
。均为待实现的设计，不改变当前部署。

后续业务实现以 [架构设计v1.4](ARCHITECTURE.md)、[已确认决策](DESIGN-DECISIONS.md) 和 [领域术语](GLOSSARY.md)
为准。Checkout固定总额、手动多笔付款，累计恰好付清才PAID；原pending交易在合约停止后继续完成，终态不重开。退款独立表/流程，不改订单状态与已付金额；费用按订单总额统一计算，默认顺序FIFO退款。商户日/周/月/终生限额规则可配置；风控默认放行并预留提交前咨询/结果后异步通知；reconciliation后置。目标支付宝/微信/银联、默认CNY，无消费者用户/余额/充值/提现/转账。业务闭环仍待后续实施。

## 构建及本地 Kubernetes 部署

六应用目录均提供`build.sh`和`deploy.sh`，也可在根目录使用统一脚本。Java 25自动使用项目mise运行时；默认显式操作`orbstack`
Docker/Kubernetes context，不改变全局context。

```bash
# 全量打包、构建镜像、部署与就绪验收
./scripts/deploy.sh all

# 单应用：仅构建，或构建并部署
./payment/build.sh
./payment/deploy.sh

# 独立就绪验收
./scripts/verify.sh
```

构建使用`mvnd clean package -pl <bootstrap列表> -am`
，默认跳过测试编译/执行。全量只执行一次Maven，再构建六个镜像；发布使用独立时间戳标签，原始清单不被改写。单应用只应用自己的Deployment/Service和namespace。脚本用法、变量和镜像复用见[脚本说明](scripts/README.md)。

镜像使用本地 OrbStack 共享镜像存储，imagePullPolicy 为 Never；其他集群需推送镜像并修改拉取策略。不要直接重新应用清单中的bootstrap示例标签覆盖脚本生成的发布标签。

启动JAR位于`<service>/<service>-bootstrap/target/`
，Docker脚本自动定位实际版本。用户本地同时启动需分别设置SERVER_PORT；各进程main与业务Clock均使用Asia/Shanghai。IDE重新加载根pom后使用对应
`*-bootstrap`模块运行。

## 验收

`verify.sh` 检查六个 Deployment 就绪及对应 Service 的 ready EndpointSlice。各应用保留Actuator
liveness/readiness与Kubernetes探针；此阶段仅验收启动与技术健康，不包含RPC调用或业务接口验收。

服务注册采用 Kubernetes 原生模型：Deployment labels → Service selector → EndpointSlice → DNS，无 Nacos/Eureka 客户端。可用
`kubectl -n loadup-pay get pods,svc,endpointslices` 查看。

参考：[Kubernetes Service](https://kubernetes.io/docs/concepts/services-networking/service/)。

## 历史验收（2026-10-20，重命名前）

- Java 25 下 `mvnd clean package -DskipTests` 成功，未执行单元测试或框架全量构建。
- 两个容器镜像构建成功，部署至本机 OrbStack 的旧 `loadup-wallet` namespace。
- 两个 Deployment rollout 成功，Service 均有 ready EndpointSlice。
- 当时测试用RPC连通性验收成功；该测试RPC及专用健康检查现已删除。

以上为重命名前的验收记录。重新构建并部署新版本后，使用当前 `scripts/verify.sh` 验收 `loadup-pay` namespace。

## Headlamp Web UI

部署清单固定使用 Headlamp v0.46.0，清单为 `deploy/headlamp.yml`。下列账号与权限指向新命名资源，需应用新版清单后使用。

```bash
./scripts/headlamp.sh install
./scripts/headlamp.sh open
# 另一终端生成短期登录 token，复制到登录页：
./scripts/headlamp.sh token
# macOS 也可以直接复制到剪贴板：
./scripts/headlamp.sh token | pbcopy
```

访问 http://127.0.0.1:14466 ，选择 Token 登录，再选择 `loadup-pay` namespace。

- Services：查看 acquire/payment 的服务地址和端口。
- Pods：查看实例状态、事件和日志。
- Deployments：选择 acquire 或 payment，调整副本数为 0 下线，恢复为 1 上线；可修改镜像发布版本。
- 手工缩放后不要直接重新 apply pay.yml，清单中的 replicas: 1 会恢复应用上线。

登录账户 `headlamp/pay-operator` 仅可查询收单平台工作负载、服务、日志、事件、EndpointSlice，并修改六个核心服务的 Deployment
及副本数；可列出 namespace，不授予集群管理员、Secret 或其他 namespace 工作负载权限。其余管理页面可能提示无权限。权限清单已更新，尚未应用到集群。

Token 请求有效期为 8 小时，以 API Server 签发期限为准；不写入文件或 Git。Web UI 仅通过绑定 127.0.0.1 的 port-forward
暴露，关闭转发后访问入口失效；再次执行 open 可恢复。

参考：[Headlamp 官方登录及安装说明](https://headlamp.dev/docs/latest/installation/)。

重命名前的 Headlamp 于 2026-10-20 在 OrbStack 部署验收：Deployment 1/1 就绪，本机入口 HTTP 200；RBAC 检查确认服务查询和
acquire 缩放允许、Secret 查询拒绝。当时 acquire/payment 均为 1/1 就绪，未执行实际下线操作。新 `pay-operator` 账号尚未进行集群验收。

## 本地时区约定

本项目 Kubernetes 工作负载统一使用 `Asia/Shanghai`（UTC+08:00）。六个应用均设置 `TZ=Asia/Shanghai` 及
`JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai`，确保容器与 JVM 默认时区一致。Headlamp 镜像不包含时区数据库，通过同
namespace 的 `headlamp-timezone` ConfigMap 挂载 Shanghai TZif 文件到 `/etc/localtime` 和标准 zoneinfo 路径，同时设置
TZ；无需依赖节点 hostPath。时区二进制数据取自本机 `/usr/share/zoneinfo/Asia/Shanghai`。

Kubernetes API 中 creationTimestamp 等标准时间戳仍采用 UTC/RFC3339 协议格式；这不受容器 TZ 控制。节点、CoreDNS
等系统组件不在本项目应用时区配置范围内。

时区变更已于 2026-10-20 实测：三个容器 `date` 均显示 CST +0800，两个 JVM 均报告 `user.timezone=Asia/Shanghai`，Java 应用日志显示
`+08:00`；三个 Deployment 均完成滚动更新。

customer 已补齐首批授权/资格业务源码，包含 COLA 应用层、MyBatis-Flex 持久化、Flyway 与认证 HTTP
入口。业务装配和验收状态见 [customer说明](customer/README.md)；默认构建和现有部署仍为基础启动骨架。

product 已补齐持久订单准入、条款编译和认证HTTP入口源码，运行及验收边界见[product说明](product/README.md)
。初始限额可靠发布/Outbox/ACK与失败重放源码已补；acquire创单与渠道执行继续按ROADMAP推进，新增业务尚未运行验收。

limitcenter 已补齐规则激活、全部额度桶原子预占与幂等查询的业务源码，见[limitcenter说明](limitcenter/README.md)
。执行许可/校验与显式未执行预占过期源码已补；支付结果确认/释放、退款恢复及自动扫描继续后续实现，两批均未构建或部署。

acquire 已补齐普通创单/查询、稳定创建意图、订单与条款/费用持久化源码；业务 profile 和验收范围见[acquire说明](acquire/README.md)。付款执行、收银台及自动关单仍待后续实施。

acquire 原单会话签发/幂等重放、订单范围能力认证和只读 `/checkout` 页面已有源码；实际付款入口仍待接入。新增公开地址及会话密钥配置见[acquire说明](acquire/README.md)。

acquire 已补 Checkout 一步创单源码：订单、条款、费用与首次会话在 A1 同事务提交，重试返回原链接；前期按当前模型直接迭代，详见[结构说明](design/DATABASE.md#12-checkout-一步创单结构)。本批仅静态检查，未构建、执行测试、迁移或部署。

payment 已补付款受理/查询、服务身份授权及父/单笔门禁检查的 COLA 业务源码；受理只落 INIT，不代表向渠道扣款。payment-business bootstrap 与新增测试源码已通过窄范围 mvnd 编译，测试未执行，详见[payment说明](payment/README.md)。

payment 已补父订单封禁、收敛查询和单笔关闭：未创建或从未尝试的 INIT 可持久产生未收款证据；处理中/未知只进 CLOSING。控制回执固定请求与证据，终态不重开。IDEA 检查及窄范围业务/测试源码编译记录见[payment说明](payment/README.md)，渠道关闭和金额释放仍待接入。

acquire 已补商户 `/api/acquire/payments/start` 的固定金额占用及 payment 服务受理调用，并新增 `/refresh` 权威结果收敛源码：成功累计已付、具备未收款证据且单笔执行封禁后才释放。回执防止重复落账，详见[acquire说明](acquire/README.md)。尚无实际渠道执行；最新结果收敛修改按用户要求暂停验证，未部署。

acquire 已新增 `/api/acquire/orders/close` 显式关单源码：先持久禁止新付款，再推进父封禁/逐笔关闭/结果落账，满足远端收敛及本地占用清零后才 CLOSED；未知保留 CLOSING，原请求可继续推进。从未受理的付款以独立可信事实释放，不伪造付款版本。详见[acquire说明](acquire/README.md)，本批按要求暂未验证或部署。

acquire 已新增 `/api/acquire/orders/extend` 延期源码：按原冻结合约和首次截止计算累计上限，订单锁内更新截止/版本及幂等回执，不延长已有收银台会话。详见[acquire说明](acquire/README.md)；本批暂未验证、迁移或部署。

当前付款范围为付款码（商户扫码枪输入）、JSAPI、H5，支持商户纯 API 调用；托管收银台为可选接入。Native 二维码和原生 APP 支付不在本次范围，最新边界见[架构说明](ARCHITECTURE.md)。付款码/JSAPI 安全输入准备、纯 API start 与 DirectPay 受理编排源码已补；真实渠道执行仍待接通，新增源码未验证。
