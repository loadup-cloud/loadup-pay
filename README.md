# LoadUp Pay

面向商户和子商户的外部渠道收单平台。当前 reactor 包含七个业务服务和渠道插件聚合，采用 Java 25、Spring Boot 4.1.1、LoadUp Framework 0.0.2-SNAPSHOT、Dubbo 3.3.6；Pay 自身版本为 0.0.1-SNAPSHOT。

## 模块导航

| 模块 | 职责 |
|---|---|
| [customer](customer/README.md) | 商户资格、关系与授权 |
| [product](product/README.md) | 产品合约、下单准入、规则发布 |
| [acquire](acquire/README.md) | 订单、Checkout/DirectPay、多笔付款占用与通知 |
| [payment](payment/README.md) | 付款、退款、渠道绑定、回调及未知结果恢复 |
| [limitcenter](limitcenter/README.md) | 限额预占、执行门禁、确认与释放 |
| [realtime](realtime/README.md) | 商户查询投影和重放 |
| [reconciliation](reconciliation/README.md) | 后置账单核对、差错与独立复核 |
| [channel](channel/README.md) | 支付宝、微信协议适配与公共渠道能力 |

架构、RPC 与数据库约束见 [ARCHITECTURE](ARCHITECTURE.md)，脚本用法见 [scripts](scripts/README.md)，监控部署见 [deploy](deploy/README.md)。

## 本地准备

1. 准备 Java 25、Maven Daemon，以及 LoadUp Framework 对应版本的制品。
2. 为七个业务服务准备独立 PostgreSQL 数据库或隔离 schema。配置对应 `<SERVICE>_JDBC_URL`、`<SERVICE>_JDBC_USERNAME`、`<SERVICE>_JDBC_PASSWORD`；不要直接复用旧 MySQL 库。
3. 按各 bootstrap 的 `application.yml` 配置 JWT issuer/audience/JWK、受信租户、服务令牌挂载及所用渠道/KMS/存储参数。内部 Triple 默认 50051，HTTP 默认 8080；本地同时运行需区分端口及服务目标地址。
4. 选择所需模块的 build/start 脚本。编译必须使用 `clean`，避免旧生成代码或已删除迁移留在构建产物中。数据库初始化、启动和真实渠道验收由开发者执行。

当前迁移由每个服务的 infrastructure 初始文件及实际依赖组件的迁移共同组成。初次初始化使用可丢弃开发库，不自动对既有库 clean/repair 或搬迁数据。

## 实现与验证边界

仓库已有应用编排、HTTP/Triple 适配、持久化、渠道调用、可靠事件与恢复任务源码。源码存在和历史测试记录不代表当前 PostgreSQL 基线已完成业务环境验收。

各模块 README 记录接入方式与能力限制；协议以 client 的 `.proto`/DTO、配置以 bootstrap 资源、数据结构以当前迁移文件为准。本次文档整理没有执行构建、测试、数据库迁移、部署或真实金融调用。


## 手动质量与维护工具

根 POM 对齐 Framework 的 Spotless、License、SpotBugs（含 FindSecBugs）、Enforcer、PMD、Checkstyle、JaCoCo 和 Versions 配置及版本。
这些插件没有生命周期执行绑定，正常 `compile`、`test`、`verify`、`install` 不会自动触发这些工具。以下命令在 Pay 根目录手动执行；可以添加 `-pl <模块路径>` 缩小范围。

| 用途 | 命令 |
|---|---|
| 检查 / 修复格式 | `mvnd spotless:check` / `mvnd spotless:apply` |
| 检查 / 更新 Java 许可证头 | `mvnd license:check-file-header` / `mvnd license:update-file-header` |
| 检查依赖禁用规则 | `mvnd enforcer:enforce` |
| PMD / Checkstyle 检查 | `mvnd pmd:check` / `mvnd checkstyle:check` |
| 检查可更新依赖 / 插件 | `mvnd versions:display-dependency-updates` / `mvnd versions:display-plugin-updates` |

SpotBugs 需要编译产物，例如 `mvnd -pl channel/channel-api -am clean compile spotbugs:check`。
JaCoCo 不会自动注入测试代理；需要覆盖率时显式执行，例如 `mvnd -pl channel/channel-api -am clean jacoco:prepare-agent test jacoco:report`。
Checkstyle 和 SpotBugs 使用仓库内 `config/quality/` 下与 Framework 同步的规则文件。
