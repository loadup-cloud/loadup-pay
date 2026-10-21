# LoadUp Pay

LoadUp 收单支付平台：面向商户及子商户的外部渠道收单、付款与退款。

当前为六应用初始化与契约层：Java 25、Spring Boot 4.1.1，通过 LoadUp BOM 管理依赖。独立于框架集成测试启动器。启动装配暂不依赖数据库、Redis 或注册中心。

- payment：HTTP 8080，gRPC 9090，提供 `loadup.pay.v1.PaymentProbe/Ping`。
- acquire：HTTP 8080，通过 Kubernetes Service DNS 调用 payment。
- customer、product、limitcenter、realtime：独立Bootstrap，HTTP 8080与Actuator基础健康入口。
- pay-contract：共享 protobuf wire contract，使用 gRPC MethodDescriptor，无需生成代码。
- 每个服务已拆为client/domain/bootstrap；业务Facade仍是接口，尚未提供下单、支付、退款的应用服务或Controller。

当前仅验证连通性，不创建支付、不记账。gRPC 为集群内明文通信，生产上线前需补充认证、TLS 与业务契约。

本轮结果与优先级见 [第一阶段实施说明](IMPLEMENTATION.md)。acquire/payment已迁到LoadUp原生gRPC binder，Ping报文保持不变；本轮未构建、运行测试或部署，当前源码尚未进行运行验收。

## 架构设计

详细设计入口：[COLA DDD与关键流程](DETAILED-DESIGN.md)、[数据库与DDL草案](design/DATABASE.md)、[Client API与通知协议](design/CLIENT-API.md)、[合约参数](design/CONTRACT-SCHEMA.md)。均为待实现的设计，不改变当前部署。

后续业务实现以 [架构设计v1.4](ARCHITECTURE.md)、[已确认决策](DESIGN-DECISIONS.md) 和 [领域术语](GLOSSARY.md) 为准。Checkout固定总额、手动多笔付款，累计恰好付清才PAID；原pending交易在合约停止后继续完成，终态不重开。退款独立表/流程，不改订单状态与已付金额；费用按订单总额统一计算，默认顺序FIFO退款。商户日/周/月/终生限额规则可配置；风控默认放行并预留提交前咨询/结果后异步通知；reconciliation后置。目标支付宝/微信/银联、默认CNY，无消费者用户/余额/充值/提现/转账。当前运行仍为两应用Ping骨架，以上业务设计尚未实现。

## 构建及本地 Kubernetes 部署

2026-10-21 已统一源码、模块、配置、部署清单及设计标识；迁移步骤见 [重命名说明](RENAMING.md)。本次未构建或发布，新名称下的部署尚未验收。OrbStack API 当前连接被拒绝，无法确认已有工作负载状态。

在本目录执行（本机 Java 25 由 mise 管理）：

```bash
export JAVA_HOME=/Users/lise/.local/share/mise/installs/java/liberica-25.0.4.1+1
mvnd clean package -DskipTests
docker build --build-arg APP=payment -t loadup-pay/payment:dev .
docker build --build-arg APP=acquire -t loadup-pay/acquire:bootstrap .
for app in customer product limitcenter realtime; do
  docker build --build-arg APP="$app" -t "loadup-pay/$app:bootstrap" .
done
kubectl --context orbstack apply -f deploy/pay.yml
./scripts/verify.sh
```

镜像使用本地 OrbStack 共享镜像存储，imagePullPolicy 为 Never；其他集群需推送镜像并修改 image 和拉取策略。脚本使用当前 kubectl context，运行前确认目标集群。

启动JAR现位于`<service>/<service>-bootstrap/target/<service>-bootstrap-0.0.1-SNAPSHOT.jar`。本地同时启动需分别设置SERVER_PORT；各进程main与业务Clock均使用Asia/Shanghai。IDE重新加载根pom后使用对应`*-bootstrap`模块运行。

## 验收

`verify.sh` 检查六个 Deployment 就绪及对应 Service 的 ready EndpointSlice，再通过 acquire 的 `/actuator/health/paymentRpc` 发起真实 gRPC 请求。只接受 `payment:acquire` 的响应。RPC 超时为 3 秒，失败返回健康状态 DOWN 和 HTTP 503。依赖探针独立于 liveness/readiness，避免 payment 故障导致 acquire 重启。其余四服务此阶段仅验收启动与技术健康，不代表业务接口可调用。

服务注册采用 Kubernetes 原生模型：Deployment labels → Service selector → EndpointSlice → DNS，无 Nacos/Eureka 客户端。可用 `kubectl -n loadup-pay get pods,svc,endpointslices` 查看。

参考：[Kubernetes Service](https://kubernetes.io/docs/concepts/services-networking/service/)、[gRPC Java](https://grpc.io/docs/languages/java/basics/)。

## 历史验收（2026-10-20，重命名前）

- Java 25 下 `mvnd clean package -DskipTests` 成功，未执行单元测试或框架全量构建。
- 两个容器镜像构建成功，部署至本机 OrbStack 的旧 `loadup-wallet` namespace。
- 两个 Deployment rollout 成功，Service 均有 ready EndpointSlice。
- `scripts/verify.sh` 实际执行成功：acquire 经 DNS 访问 payment，gRPC 响应校验为 `payment:acquire`。
- acquire 部署通过 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always` 开启子健康检查入口。

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

登录账户 `headlamp/pay-operator` 仅可查询收单平台工作负载、服务、日志、事件、EndpointSlice，并修改六个核心服务的 Deployment 及副本数；可列出 namespace，不授予集群管理员、Secret 或其他 namespace 工作负载权限。其余管理页面可能提示无权限。权限清单已更新，尚未应用到集群。

Token 请求有效期为 8 小时，以 API Server 签发期限为准；不写入文件或 Git。Web UI 仅通过绑定 127.0.0.1 的 port-forward 暴露，关闭转发后访问入口失效；再次执行 open 可恢复。

参考：[Headlamp 官方登录及安装说明](https://headlamp.dev/docs/latest/installation/)。

重命名前的 Headlamp 于 2026-10-20 在 OrbStack 部署验收：Deployment 1/1 就绪，本机入口 HTTP 200；RBAC 检查确认服务查询和 acquire 缩放允许、Secret 查询拒绝。当时 acquire/payment 均为 1/1 就绪，未执行实际下线操作。新 `pay-operator` 账号尚未进行集群验收。

## 本地时区约定

本项目 Kubernetes 工作负载统一使用 `Asia/Shanghai`（UTC+08:00）。acquire/payment 设置 `TZ=Asia/Shanghai` 及 `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai`，确保容器与 JVM 默认时区一致。Headlamp 镜像不包含时区数据库，通过同 namespace 的 `headlamp-timezone` ConfigMap 挂载 Shanghai TZif 文件到 `/etc/localtime` 和标准 zoneinfo 路径，同时设置 TZ；无需依赖节点 hostPath。时区二进制数据取自本机 `/usr/share/zoneinfo/Asia/Shanghai`。

Kubernetes API 中 creationTimestamp 等标准时间戳仍采用 UTC/RFC3339 协议格式；这不受容器 TZ 控制。节点、CoreDNS 等系统组件不在本项目应用时区配置范围内。

时区变更已于 2026-10-20 实测：三个容器 `date` 均显示 CST +0800，两个 JVM 均报告 `user.timezone=Asia/Shanghai`，Java 应用日志显示 `+08:00`；三个 Deployment 均完成滚动更新。
