# LoadUp Pay 重命名说明

日期：2026-10-21 ｜ 状态：源码及设计引用已统一；集群迁移未执行

项目中文名为 **LoadUp 收单支付平台**，英文名为 **LoadUp Pay**，工程目录与根artifactId为 `loadup-pay`。各业务服务继续使用 acquire、payment、customer、product、limitcenter、realtime。

## 1. 名称映射

| 原名称 | 新名称 |
|---|---|
| LoadUp Wallet / loadup-wallet | LoadUp Pay / loadup-pay |
| wallet-contract | pay-contract |
| io.github.loadup.wallet.* | io.github.loadup.pay.* |
| loadup.wallet.v1.PaymentProbe | loadup.pay.v1.PaymentProbe |
| wallet.payment-target / wallet.grpc-port | pay.payment-target / pay.grpc-port |
| deploy/wallet.yml | deploy/pay.yml |
| loadup-wallet namespace、镜像前缀、服务DNS | loadup-pay namespace、镜像前缀、服务DNS |
| headlamp/wallet-operator | headlamp/pay-operator |
| headlamp-wallet-operator / headlamp-wallet-namespaces | headlamp-pay-operator / headlamp-pay-namespaces |
| wallet.* 事件/任务、WalletEventEnvelope、wallet-checkout-v1 | pay.* 事件/任务、PayEventEnvelope、pay-checkout-v1 |

目录、Java包名、gRPC包及根Maven坐标在本次开始前已经部分改好；本次完成剩余引用。根groupId沿用当前的 `io.github.loadup-cloud`；模块版本和LoadUp BOM不变。IDE的Maven导入路径、契约模块与运行主类引用也已同步。

技术配置继续支持原有环境变量 `PAYMENT_TARGET`、`GRPC_PORT`，只修改应用内部配置前缀。HTTP端口8080、gRPC端口9090、`payment:acquire`响应、探针和超时、Asia/Shanghai时区、Headlamp版本及RBAC权限范围均保持原设计。

事件/任务、业务token和业务client尚未实现，此处修改的是设计契约，不涉及已持久化事件、会话或业务数据库迁移。`target/`为旧构建产物，未手改或删除；使用新源码前需按README进行clean构建，不能直接复用旧JAR作为新镜像。

## 2. 本地验证范围

本次采用静态证据核对Maven父子/依赖、Java包路径/导入、配置引用、proto与手工MethodDescriptor、YAML和RBAC资源引用、shell语法以及文档链接。没有执行Maven构建、测试、镜像构建、kubectl apply或缩容/删除。

2026-10-21只读查询OrbStack API时连接被拒绝，无法确认现有namespace和工作负载状态。README中的2026-10-20记录为重命名前历史验收，不能作为新版部署通过的证据。

## 3. 后续部署与回退顺序

1. 在 `loadup-pay` 目录按README执行clean构建，重新构建两个 `loadup-pay/*` 镜像。acquire/payment成对发布，使用同一版本的pay-contract。
2. 应用 `deploy/pay.yml`，在新 `loadup-pay` namespace内连接 `payment.loadup-pay.svc.cluster.local`；再应用Headlamp清单，创建新pay-operator及其绑定。
3. 执行 `scripts/verify.sh` 验证新namespace的Deployment、EndpointSlice和真实gRPC响应，再验证Headlamp新账号的资源范围和时区。
4. 确认新环境通过后再切换本地访问/调用入口。旧namespace、镜像和旧Headlamp角色不会被本次文件重命名自动删除，后续清理单独进行。

新旧namespace可并存，两个环境各自连接自己的payment，避免旧/新gRPC全限定名混用。若新部署未通过，继续使用原环境和原访问入口；若已经切换，先将入口切回已验证的原环境。回退时不直接把旧镜像替换到新命名且已配置新DNS的混合环境。

源码回退使用修改前快照或版本控制，还原对应POM、配置、契约模块目录、部署文件名及IDE引用为同一套版本；不要只回退其中一个调用方。本次没有执行集群切换或清理步骤。
