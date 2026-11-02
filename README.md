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

## 本地数据库配置

七个 bootstrap 均提供 `application-local.yml`。启动时启用 `local` profile，例如在 IDEA 的 Active profiles 中填写 `local`，或传入 `--spring.profiles.active=local`。

本地 PostgreSQL 使用 `admin` / `123456`，每个服务使用独立数据库，schema 统一使用默认的 `public`：

| 服务 | 数据库 | schema | HTTP | RPC |
|---|---|---|---|---|
| payment | loadup_payment | public | 8081 | 50051 |
| acquire | loadup_acquire | public | 8082 | 50052 |
| customer | loadup_customer | public | 8083 | 50053 |
| limitcenter | loadup_limitcenter | public | 8084 | 50054 |
| product | loadup_product | public | 8085 | 50055 |
| realtime | loadup_realtime | public | 8086 | 50056 |
| reconciliation | loadup_reconciliation | public | 8087 | 50057 |

初始化使用 `scripts/init-local-databases.sql`，以 `admin` 通过 psql 执行，不包裹事务。脚本可重复执行，仅创建缺失的数据库（PostgreSQL 自动提供 `public` schema）。当前本地 OrbStack PostgreSQL 可执行：

```bash
docker --context orbstack exec -i postgres psql -U admin -d loadup -v ON_ERROR_STOP=1 < scripts/init-local-databases.sql
```

本地配置沿用 PostgreSQL 和框架的默认 `public` schema，业务 SQL 与 Flyway 迁移均使用该 schema。Outbox 等组件的通用迁移由依赖 JAR 提供，各服务在自己的数据库中执行并维护独立的迁移历史，无需复制或添加服务专用的组件脚本。启用 local profile 后，其他 JWT、服务令牌、渠道及存储配置仍按各服务要求提供。

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

## RPC 调用默认配置

服务地址由 Dubbo 和 K8s DNS 管理。调用身份统一使用框架默认值，无需为每个目标服务重复配置：

| 参数 | 默认值 |
|---|---|
| tenant-id | `__default__` |
| service-id | 当前 `spring.application.name` |
| audience | 目标服务名，例如 `limitcenter` |
| token-file | `/var/run/loadup/secrets/<目标服务名>.jwt` |

需要统一覆盖时仅配置一次：

```yaml
loadup:
  rpc:
    client:
      tenant-id: tenant_a
      audience: internal
      token-file: /var/run/loadup/secrets/service.jwt
```

单个调用有例外时，在原有应用配置路径覆盖对应字段即可，例如 `loadup.modules.payment.limitcenter.audience`；未覆盖的字段继续继承公共默认值。优先级为：调用专用配置 > `loadup.rpc.client` > 框架约定。各服务的 HTTP audience 默认也遵循公共 audience，可用原有 `<SERVICE>_JWT_AUDIENCE` 单独覆盖。服务令牌仍需由发行方提供，并与实际租户、调用方、audience 匹配；默认配置不生成或跳过令牌校验。
