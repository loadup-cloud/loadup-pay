# 架构门禁与能力状态维护

更新：2026-10-27。对应 review R8；目前只有门禁源码，尚未编译或执行。

## 状态入口

- 根 README：唯一的当前能力矩阵，逐项记录源码、自动化运行、当前业务环境验收及责任模块。
- 模块 README：集成契约、配置、边界和该模块的证据范围。
- ARCHITECTURE / 详细设计：所有权、事务、权限及目标不变量，不复制每日实现状态。
- ROADMAP：未完成的业务与验收队列；带日期段落为历史轨迹。
- BUSINESS-IMPLEMENTATION / IMPLEMENTATION：业务与早期初始化日志；不把旧阶段描述当成当前事实。
- design/history：整理前原文快照，保留历史声明，不作为本轮通过证据。

状态变更应同时回答“写了什么、实际运行了什么、在哪个环境验收了什么”。新增测试文件只能登记为源码。
修改过源代码后，历史编译/测试或旧镜像启动不自动覆盖新版本。实际运行必须记录命令、范围、结果与未覆盖项。

## 现有渠道门禁

`channel/channel-core/src/test/java/io/github/loadup/pay/channel/core/ChannelModuleBoundaryTest.java` 保留渠道独立性、payment
只依赖 channel-api、插件只由 bootstrap runtime 装配等检查。
本轮没有运行该测试，也没有把旧门禁源码存在视为当前工程已经满足约束。

## 新增七服务源码/POM 门禁

`PayModuleBoundaryTest` 放在现有 channel-core 的测试目录，复用当前测试依赖；没有新增治理服务、平台或测试模块。
从完整 loadup-pay checkout 中读取 acquire/payment/customer/product/limitcenter/realtime/reconciliation 的
client/domain/app/infrastructure/adapter/rpc/bootstrap。
缺失预期模块会失败，不以空目录扫描默认为通过。

| 范围                   | 源码/POM 规则                                                                                                                      |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| client/domain 技术纯净 | 不点名 Spring、ORM、gRPC、Protobuf 或技术组件实现；允许 JDK、commons 及纯 channel-api/client 契约                                  |
| client/domain/app 分层 | client 不引用自身 domain/实现，domain 不引用 app/实现，app 不引用 infrastructure/adapter/rpc/bootstrap                             |
| 跨服务边界             | 仅依赖对方 client 或传输适配所需 rpc，不引用对方 domain/app/infrastructure/adapter/bootstrap；client/domain/app 不直接引用对方 rpc |
| 渠道插件               | 七服务生产 Java 只命名 channel-api；POM 仅 payment-bootstrap 可用 runtime 装配 channel-core/alipay/wechat 等实现                   |
| 存储适配器到 app       | infrastructure 下 repository/codec/converter 只能引用本服务 app.port/model/converter，不引用应用服务或安全编排                     |
| POM 覆盖               | 检查直接依赖及 profile 依赖；test scope 不作为生产依赖违规，以保留真实 Gateway/编排集成测试                                        |

infrastructure → app 用于实现 port、持久化恢复模型及复用纯 MapStruct 映射，是当前仓库的既有依赖倒置；不按层名一律禁止。
映射接口不应通过命名规避服务边界；新行为仍需在负责层实现。
原生 task/outbox 等技术入站适配器目前位于 infrastructure，允许调用本服务现有应用入口；此门禁没有宣称这部分已全部收敛为
port/model。
后续按 R4–R7 的具体切片收敛这些适配入口，不能为一次门禁通过静默搬动交易职责或扩大提交授权。

门禁含合成反例与正例：Spring 进入 domain、自身 app→infrastructure、跨服务实现、业务层直连 RPC、插件绕过以及 codec 调应用服务应被拒绝；
合法 client、channel-api、port/model、MapStruct 映射、基础设施 gRPC 适配、bootstrap runtime 和 test scope 适配应允许。
这些正反例本轮同样未运行，因此尚不构成“故意破坏后门禁失败”的实测证据。

## 限制与待补证明

源码检查读取显式全限定引用，POM 检查读取声明的直接/profile 依赖；不是 Java 编译器、effective-POM 或完整传递依赖分析。
它不证明生成代码、运行时反射、Spring 装配、字节码依赖、SQL/事务或真实 RPC 安全，也不覆盖厂商 SDK 的全部传递实现。
字符串/注释中的显式实现包名也可能被视为依赖点名；新增例外须以真实职责解释，不能添加宽泛忽略列表。

R8 尚未完成：仍需门禁编译/实际正反例运行、正常构建时的字节码依赖证明，以及其余技术入站适配接口的约束收敛。
R1 的实际 MySQL/并发/回滚证明与 R2 锁竞争验收保持独立，不能用本门禁替代。

用户暂停验证期间不运行下列命令。恢复验证后可选择窄范围（需已安装依赖）：

```bash
mvnd clean test -pl channel/channel-core -Dtest=ChannelModuleBoundaryTest,PayModuleBoundaryTest -Dskip.spotless=true -Dskip.spotbugs=true
```

本轮未运行 IDE、编译、测试、容器、DDL、部署或外部调用。
