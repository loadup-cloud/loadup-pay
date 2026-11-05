# LoadUp Pay

面向商户和子商户的外部渠道收单平台。当前 reactor 包含十一个应用；支付宝/微信渠道协议由 LoadUp Framework 的
`loadup-components-payment-channel` 组件族提供。项目采用 Java 25、Spring Boot 4.1.2、LoadUp Framework
0.0.2-SNAPSHOT、Spring HTTP Service Clients；Pay 自身版本为 0.0.1-SNAPSHOT。

## 模块导航

| 模块 | 职责 |
|---|---|
| [merchantcenter](merchantcenter/README.md) | 商户主档、资格、关系与授权核心 |
| [merchantmng](merchantmng/README.md) | 商户后管与签约入口 |
| [productcenter](productcenter/README.md) | 产品、合约、下单准入、规则发布核心 |
| [productmng](productmng/README.md) | 产品与合约后管 |
| [notificationcenter](notificationcenter/README.md) | 通用通知中心；当前组装站内信，可靠渠道投递按设计分阶段实施 |
| [authserver](authserver/README.md) | 应用凭证和令牌签发 |
| [acquire](acquire/README.md) | 订单、Checkout/DirectPay、多笔付款占用与通知 |
| [payment](payment/README.md) | 付款、退款、渠道绑定、回调及未知结果恢复 |
| [limitcenter](limitcenter/README.md) | 限额预占、执行门禁、确认与释放 |
| [realtime](realtime/README.md) | 商户查询投影和重放 |
| [reconciliation](reconciliation/README.md) | 后置账单核对、差错与独立复核 |

渠道 SPI、路由引擎和支付宝/微信协议 binder 位于
[Framework payment-channel](../loadup-framework/loadup-components/integration/loadup-components-payment-channel/README.md)；
payment 通过 `loadup-components-payment-channel-api` 调用统一 Facade，bootstrap 以 runtime 依赖装配 core 和厂商 binder。

通知体系设计及实施状态见 [Notification Center](notificationcenter/ARCHITECTURE.md)。

架构、RPC 与数据库约束见 [ARCHITECTURE](ARCHITECTURE.md)，脚本用法见 [scripts](scripts/README.md)，监控部署见 [deploy](deploy/README.md)。

后续完整迁移按 [Roadmap](ROADMAP.md) 执行：K8s Service DNS、HTTP Service Clients、版本化配置及 Gateway API / Envoy Gateway 为目标默认；当前实现与迁移验收状态分别记录。

## 本地准备

1. 准备 Java 25、Maven Daemon，以及 LoadUp Framework 对应版本的制品。
2. 两个 mng 后管不使用数据库；为其余核心应用准备独立 PostgreSQL 数据库或隔离 schema。配置对应 `<SERVICE>_JDBC_URL`、`<SERVICE>_JDBC_USERNAME`、`<SERVICE>_JDBC_PASSWORD`；不要直接复用旧 MySQL 库。
3. 按各 bootstrap 的 `application.yml` 配置 JWT issuer/audience/JWK、受信租户、服务令牌挂载及所用渠道/KMS/存储参数。内部与业务入口均为 HTTP，默认 8080；本地 profile 按核心服务配置独立端口及出站基础地址。
4. 选择所需模块的 build/start 脚本。编译必须使用 `clean`，避免旧生成代码或已删除迁移留在构建产物中。数据库初始化、启动和真实渠道验收由开发者执行。

当前迁移由每个服务的 infrastructure 初始文件及实际依赖组件的迁移共同组成。初次初始化使用可丢弃开发库，不自动对既有库 clean/repair 或搬迁数据。

## 本地数据库配置

八个 bootstrap 提供 `application-local.yml`（authserver、payment、acquire、merchantcenter、productcenter、limitcenter、realtime、reconciliation）；notificationcenter 通过环境变量配置。启动时启用 `local` profile，例如在 IDEA 的 Active profiles 中填写 `local`，或传入 `--spring.profiles.active=local`。

本地 PostgreSQL 使用 `admin` / `123456`，每个服务使用独立数据库，schema 统一使用默认的 `public`：

| 服务 | 数据库 | schema | HTTP | RPC |
|---|---|---|---|---|
| payment | loadup_payment | public | 8081 | 50051 |
| acquire | loadup_acquire | public | 8082 | 50052 |
| merchantcenter | loadup_merchantcenter | public | 8083 | 50053 |
| limitcenter | loadup_limitcenter | public | 8084 | 50054 |
| productcenter | loadup_product | public | 8085 | 50055 |
| realtime | loadup_realtime | public | 8086 | 50056 |
| reconciliation | loadup_reconciliation | public | 8087 | 50057 |
| authserver | loadup_authserver | public | 8089 | - |
| notificationcenter | loadup_notificationcenter | public | 8080 | - |

初始化使用 `scripts/init-local-databases.sql`，以 `admin` 通过 psql 执行，不包裹事务。脚本可重复执行，仅创建缺失的数据库（PostgreSQL 自动提供 `public` schema）。当前本地 OrbStack PostgreSQL 可执行：

```bash
docker --context orbstack exec -i postgres psql -U admin -d loadup -v ON_ERROR_STOP=1 < scripts/init-local-databases.sql
```

本地配置沿用 PostgreSQL 和框架的默认 `public` schema，业务 SQL 与 Flyway 迁移均使用该 schema。Outbox 等组件的通用迁移由依赖 JAR 提供，各服务在自己的数据库中执行并维护独立的迁移历史，无需复制或添加服务专用的组件脚本。启用 local profile 后，其他 JWT、服务令牌、渠道及存储配置仍按各服务要求提供。

## 实现与验证边界

仓库已有应用编排、HTTP 适配、持久化、渠道调用、可靠事件与恢复任务源码。源码存在和历史测试记录不代表当前 PostgreSQL 基线已完成业务环境验收。

各模块 README 记录接入方式与能力限制；协议以 client.http 标准接口/DTO、配置以 bootstrap 资源、数据结构以当前迁移文件为准。本次文档整理没有执行构建、测试、数据库迁移、部署或真实金融调用。


## 手动质量与维护工具

根 POM 对齐 Framework 的 Spotless、Enforcer、JaCoCo 和 Versions 配置及版本。
这些插件没有生命周期执行绑定，正常 `compile`、`test`、`verify`、`install` 不会自动触发这些工具。以下命令在 Pay 根目录手动执行；可以添加 `-pl <模块路径>` 缩小范围。

| 用途 | 命令 |
|---|---|
| 检查 / 修复格式 | `mvnd spotless:check` / `mvnd spotless:apply` |
| 检查 / 更新 Java 许可证头 | `./spotless.sh check` / `./spotless.sh apply` |
| 检查依赖禁用规则 | `mvnd enforcer:enforce` |
| 检查可更新依赖 / 插件 | `mvnd versions:display-dependency-updates` / `mvnd versions:display-plugin-updates` |

SpotBugs 需要编译产物，例如 `mvnd -pl payment/payment-infrastructure -am clean compile spotbugs:check`。
JaCoCo 不会自动注入测试代理；需要覆盖率时显式执行，例如
`mvnd -pl payment/payment-app -am clean jacoco:prepare-agent test jacoco:report`。
Checkstyle 和 SpotBugs 使用仓库内 `config/quality/` 下与 Framework 同步的规则文件。

## HTTP 调用默认配置

Service Client 以目标应用名命名，默认地址为 `http://<目标应用名>:8080`，K8s 普通 Service DNS 直接适用；本地 profile 只覆盖
`base-url` 为 localhost 端口。超时、连接池、请求/响应大小和私有地址策略使用框架默认值，无需为每个目标重复配置。只有地址或参数
不同于约定的客户端（例如单列连接池的对账账单下载、外部商户地址）才在应用配置中单独声明。

调用身份统一使用框架默认值，无需为每个目标服务重复配置：

| 参数 | 默认值 |
|---|---|
| tenant-id | `__default__` |
| service-id | 当前 `spring.application.name` |
| audience | 目标服务名，例如 `limitcenter` |
| client-id | `<service-id>.default.<audience>` |

需要统一覆盖时仅配置一次：

```yaml
loadup:
  http:
    client:
      tenant-id: tenant_a
      audience: internal
```

单个调用有例外时，在原有应用配置路径覆盖对应字段即可，例如 `loadup.modules.payment.limitcenter.audience`；未覆盖的字段继续继承公共默认值。优先级为：调用专用配置 > `loadup.http.client` > 框架约定。service-auth 注册项只需声明 `scopes`，身份字段按上表推导；入站 HTTP audience 使用各服务的 `spring.application.name`，不再需要 `<SERVICE>_JWT_AUDIENCE`。出站目标 audience 可通过模块配置分别覆盖。服务令牌由独立 AuthServer 按租户、调用方、目标服务及权限签发；共享 OAuth 客户端负责缓存和续取，资源端继续验签与授权。见 [AuthServer](authserver/README.md)。

K8s 发布显式绑定 CONFIG_VERSION/SECRET_VERSION，仅 AuthServer 挂载其签名 KeyStore。资源准备、预览、排空和回退见 [配置与发布](deploy/CONFIGURATION.md)。

平台入口配置见 [Gateway API / Envoy Gateway](deploy/gateway/README.md)，HTTP 入站选择器见 [网络策略](deploy/NETWORK_POLICY.md)。

线程模型使用显式 virtual-threads 候选 profile，默认平台线程；正确性、A/B 容量及七服务收口见 [最终验收](deploy/ACCEPTANCE.md)。
