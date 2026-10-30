# LoadUp — 项目协作指南

本文件从 Git 历史中的原版恢复，并以当前仓库结构和约定更新。具体组件契约以对应模块的 `README.md` 和 `ARCHITECTURE.md` 为准；
`docs/` 是面向读者的 Hextra 文档站，不作为代码实现的依据。

## 前期开发规则

- 当前阶段不要求向后兼容；不合理的设计或代码可直接重构或删除，包括 API、领域模型和数据库结构。
- 统一使用当前模型并同步调用方、测试和设计文档；无需为旧版本保留兼容重载、适配分支或混合版本发布方案。
- 后续只有用户明确提出兼容要求时，才按其指定范围设计兼容方案。本规则优先于本文其他兼容性约定。

## Selected-model lead and Luna delegation policy

- Keep the lead agent on the model and reasoning effort selected for this thread. Architecture, ambiguous diagnosis,
  cross-module semantics, security, production operations, deployment, database writes, Git push, and final acceptance
  remain with the lead.
- Delegate separable, bounded, low-risk execution only to the custom `luna_worker` agent, with at most two workers
  running concurrently. Allowed work: independent code search, focused test execution, mechanical localized edits, and
  evidence collection.
- Before dispatch, specify allowed scope, affected files, acceptance checks, explicit non-goals, and exact commands or
  required evidence. Spawn with `agent_type = "luna_worker"` and independent, self-contained context; use no default or
  built-in worker role for execution.
- If `luna_worker` or the required agent-type selection is unavailable, keep the work with the lead and report the
  limitation. Do not substitute another agent or model.
- A blocked worker reports the blocker and stops. It must not broaden scope or make external or destructive changes.
- The lead reviews returned diffs and completes required verification before reporting completion.

<!-- CODEGRAPH_START -->

## CodeGraph

仓库根目录存在 `.codegraph/` 时，定位或理解代码应先使用 CodeGraph，再使用 grep/find 或直接读文件。优先调用
`codegraph_explore`；工具不可用时运行 `codegraph explore "<symbol names or question>"`。若无 `.codegraph/`，跳过此步骤。
<!-- CODEGRAPH_END -->

---

## 项目定位

LoadUp 是一个 **被消费的框架/SDK**，通过 `loadup-dependencies` BOM 对外提供能力。
集成方在自有 Spring Boot / Spring Cloud 项目中引入 BOM，按需使用组件和业务模块。

- `loadup-application` 仅为集成测试验证器和本地开发启动器， **不是生产部署单元**
- `loadup-modules/` 下的 UPMS、审计、数据字典、文件资源等是 **可复用业务能力**，不是测试代码
- 单体应用通过 Spring MVC Controller 暴露接口；外部 API 调用规划为独立 HTTP 客户端组件

---

## 验证与构建

- 构建及 Maven 插件命令统一使用 Maven Daemon（`mvnd`），保留 `clean` 和按模块选择。`mvnd` 不可用时报告环境限制，不自动回退到
  `mvn`。

- IDEA MCP 可用时直接使用其代码检查能力，不编写临时手动 check 脚本代替 IDE 检查。
- 默认不构建、不运行测试；用户负责本地构建。仅在用户要求或无法以其他方式验证时使用窄范围 `mvnd clean`，不主动运行全反应堆构建。
- 测试补齐按批次集中编写与审查，先使用 IDEA MCP 批量检查，再合并受影响模块执行必要的 `mvnd clean` 验证；避免每新增一个用例就单独运行
  Maven。
- 需要验证编译、注解处理或依赖装配时使用 Maven build；不完全禁止构建。优先选择受影响模块及其依赖，用户要求整体构建时执行整体构建。
- Maven 编译必须带 `clean`，避免 MapStruct 等注解处理器增量生成时发生 `FilerException`。
- 明确区分格式化、编译、单测与真实环境集成测试；未运行的验证不得标为通过。根据任务需要选择测试和检查跳过参数，避免无收益的重复全量执行。

---

## 项目结构

```
loadup-cloud/
├── loadup-dependencies/        # BOM，统一依赖版本
├── loadup-commons/             # 最底层通用基础
│   ├── loadup-commons-context/ # JDK 25 ScopedValue 只读执行链上下文
│   ├── loadup-commons-dto/     # 通用响应、DTO 与 BaseDO
│   ├── loadup-commons-util/    # 工具类：StringUtils、DateUtils、MoneyUtil
│   ├── loadup-commons-log/     # 统一日志格式与 trace MDC 约定
├── loadup-components/          # 技术组件，分类目录不增加 Maven 层级
│   ├── security/       # authorization, authserver, resource-server, captcha, kms, signature
│   ├── web/            # webmvc, springdoc
│   ├── data/           # cache, database, dfs
│   ├── integration/    # http, gotone
│   ├── reliability/    # lock, outbox, delayedtask, globalunique, resilience4j
│   ├── execution/      # scheduler, pipeline
│   └── platform/       # configcenter, extension, observability, testcontainers
├── loadup-modules/             # 通用业务聚合，各自包含 COLA 分层与可选 web 子模块
│   ├── loadup-modules-upms/    # 用户与权限管理、账号安全
│   ├── loadup-modules-audit/   # 审计中心
│   ├── loadup-modules-dictionary/ # 数据字典
│   ├── loadup-modules-file/    # 文件资源
│   ├── loadup-modules-notification/ # 站内通知
│   └── loadup-modules-transfer/ # 导入导出任务
├── loadup-testify/             # 集成测试框架
├── loadup-application/         # 集成测试启动器（非生产部署单元）
├── frontend/                   # Vue 3 管理端
└── docs/                       # 独立的 Hugo + Hextra 文档站
```

---

## 模块依赖方向（严格单向）

```
loadup-dependencies (BOM)
        ↑
   loadup-commons/*
        ↑
  loadup-components/{category}/*   ← 通过 API/binder 模式解耦横向依赖
        ↑
   loadup-modules/*     ← 模块间依赖应明确且尽量减少
        ↑
loadup-application

loadup-testify  → 仅 test scope，深度依赖框架内部类型
```

业务模块通过可选 `*-web` 适配模块提供 Spring MVC Controller；业务服务不依赖 HTTP 客户端实现。

---

## 组件设计规范

> 项目概况见 [README.md](README.md)，待办事项见 [ROADMAP.md](ROADMAP.md)，组件设计以对应 `ARCHITECTURE.md` 为准。
> 核心原则：底层 OSS + 薄集成；业务侧 API 尽量采用业界标准接口（Spring Cache、S3、OpenTelemetry、Spring MVC
> 等）；自创接口仅限标准表达不了的语义；每个组件 README 必须维护能力矩阵契约表。

### 单后端选择模式（Mode A）

适用于一次选一种后端，例如 Cache / Captcha / ConfigCenter / DFS。

```
loadup-components-{domain}/
├── pom.xml                    # 聚合 POM（packaging: pom）
├── {domain}-api/              # {Domain}Provider SPI + {Domain}Template 业务 API
├── {domain}-binder-{impl}/    # 每个后端一个 binder 模块
└── {domain}-test/             # 集成测试
```

- 业务代码只引入 `-api`，注入 `{Domain}Template`
- 集成方通过加 `-binder-{impl}` 的 pom 依赖 + yml 配置 `binder-type` 切换后端
- 装配机制：`@ConditionalOnSingleCandidate(Provider.class)` 自动创建 Template
- 示例：cache、captcha、configcenter、dfs

### 多后端共存模式（Mode B）

适用于多个 Provider 同时活跃、运行时数据驱动路由。仅 Gotone。

```
loadup-components-{domain}/
├── pom.xml                    # 聚合 POM
├── {domain}-api/              # SPI + Template + 存储 SPI（Optional）
├── {domain}-engine/           # 纯发送引擎（零存储，零 DB 依赖）
├── {domain}-store-jdbc/       # 默认 JDBC 存储实现（可选）
├── {domain}-binder-{impl}/    # 每个渠道一个 binder 模块
└── {domain}-test/
```

- Provider 通过 `List<{Domain}Provider>` 注入到 Engine 中收集
- 存储 SPIs（`ServiceConfigProvider` / `ChannelConfigProvider` / `RecordHandler`）全部 `Optional`
- 引擎不依赖任何存储与 DB

### 单一 jar 模式（只有一个实现）

```
loadup-components-{name}/
├── pom.xml                    # 独立 jar
└── src/main/java/...
```

- 示例：signature、database、authorization、pipeline

**判断标准**：如果一个组件有多于一种后端实现 → Mode A（单后端）或 Mode B（多后端共存）。反之 → 单一 jar。

---

## 业务模块内部分层（COLA 4.0）

```
loadup-modules-{mod}/
├── {mod}-client/          # Facade、DTO、Command、Query、消费方 SPI
├── {mod}-domain/          # 纯 POJO + Gateway 接口 + 枚举（零 Spring 注解）
├── {mod}-infrastructure/  # DO extends BaseDO、Mapper、GatewayImpl、Converter
├── {mod}-app/             # @Service 业务编排、AutoConfiguration
├── {mod}-web/             # 可选 Controller 适配；按 Maven 依赖装配
└── {mod}-test/            # 集成测试 + 单元测试（parent = 根 loadup-parent）
```

**domain 层铁律**：无 `@Table`、无 `@Service`、无任何 Spring / ORM 注解。

---

## 硬性禁止项

| #  | 禁止行为                                                | 正确做法                                                                                                                                                                      |
|----|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | 生成的文件包含 LICENSE 文件头                           | 所有新生成文件均不添加 LICENSE 或版权声明头部                                                                                                                                 |
| 2  | 将 HTTP 映射直接写在业务 Service 中                     | 在可选 Web 适配模块中使用 Spring MVC Controller 调用 Service                                                                                                                  |
| 3  | 集成测试中用 `@MockBean` 替代 DB                        | `@EnableTestContainers(ContainerType.POSTGRESQL)` 启动真实容器                                                                                                                |
| 4  | `@Autowired` 字段注入                                   | 构造器注入：显式 `public XxxService(XxxGateway gw) { this.gw = gw; }`                                                                                                         |
| 5  | 字符串拼接 SQL                                          | 参数化 Spring JDBC SQL                                                                                                                                                        |
| 6  | `@Table` 放在 domain 层                                 | DO（`XxxDO extends BaseDO`）只放在 `infrastructure.dataobject`                                                                                                                |
| 7  | 子模块 `<parent>` 指向模块自身 pom                      | 所有子模块 `<parent>` 统一指向根 `loadup-parent`                                                                                                                              |
| 8  | 子模块内写 `<version>` 引用同项目模块                   | 版本由 `loadup-dependencies` BOM 统一管理                                                                                                                                     |
| 9  | 表主键 `BIGINT AUTO_INCREMENT`                          | 主键 `VARCHAR(64)`，默认由 database 的 `IdGenerator` 生成业务 ID；序列表元数据显式使用 UUID 防止递归                                                                          |
| 10 | 无必要的业务模块横向依赖                                | 保持清晰依赖方向，跨模块能力优先通过公开接口集成                                                                                                                              |
| 11 | Java 文件中写中文注释或 Javadoc                         | 注释/Javadoc 统一使用英文；中文只允许出现在 `*.md` 文档文件中                                                                                                                 |
| 12 | `BOOLEAN`/`BOOL` 类型                                   | 统一使用 `SMALLINT`（0/1），并加 CHECK                                                                                                                                        |
| 13 | DO 中重复定义 id/createdAt/updatedAt/tenantId/deleted   | 这些字段在 `BaseDO` 中已定义                                                                                                                                                  |
| 14 | Mapper 中写额外 SQL 方法                                | 用 `QueryWrapper` 在 GatewayImpl 中操作                                                                                                                                       |
| 15 | 新增三方依赖不在 BOM 中声明                             | 版本管理集中在 `loadup-dependencies/pom.xml`                                                                                                                                  |
| 16 | 使用 Lombok（`@Data`/`@Getter`/`@Slf4j`/`@Builder` 等） | 写显式 Java 代码；DTO/Command/Query 优先使用 Java `record`；日志打印使用 `LogUtil.info/warn/error(Source.class, ...)`；仅第三方日志适配需要 Logger 时使用 `LogUtil.getLogger` |

---

## 数据库表规范

创建与更新时间统一命名：Java 属性和 JSON 字段使用 `createdAt`、`updatedAt`，数据库列使用 `created_at`、`updated_at`。领域对象、DTO
和查询排序字段遵循同一命名，不使用 `createdTime`、`updatedTime`。

每张表必须包含 5 个标准字段：

```sql
id         VARCHAR(64)  NOT NULL PRIMARY KEY,
tenant_id  VARCHAR(64),
created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
deleted    SMALLINT NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1))
```

- 各服务的 PostgreSQL 初始结构统一放在 `db/migration/` 下的一个 Flyway 文件；开发库按需重建。
- Flyway 版本统一使用 14 位时间戳，文件命名为 `V<yyyyMMddHHmmss>__desc.sql`，例如
  `V20261020153000__create_user_table.sql`。

---

## 命名约定

POM 的 `<name>` 统一采用 `LoadUp <模块族> <能力> [职责/实现]`，按 artifactId 的词序展示：模块族使用
Commons、Components、Modules、Testify，例如 `LoadUp Commons JSON`、`LoadUp Components Cache Binder Redis`、
`LoadUp Modules UPMS App`。API/DTO/JSON/HTTP/KMS/JDBC/SMS/DFS/UPMS/SAS/S3
保持大写，JetCache、JobRunr、ConfigCenter、AuthServer、RetryTask、GlobalUnique、WebMVC、SpringDoc、Testcontainers、Resilience4j、NanoCaptcha
保持约定拼写。根项目、应用、BOM 分别使用 `LoadUp Framework`、`LoadUp Application`、`LoadUp Dependencies`；Gotone channels 聚合为
`LoadUp Components Gotone Channels`。详细用途写入 description，名称保持空格分隔的统一结构。 每个 POM 均显式声明简短英文
`<description>`，说明实际职责；client/domain/app/infrastructure/web/test 分别描述公开契约、领域模型、业务编排、持久化适配、HTTP
入口和测试范围，未实现的传输能力如实标明状态。

| 类型              | 规则                                     | 示例                             |
|-------------------|------------------------------------------|----------------------------------|
| 数据库映射对象    | `XxxDO extends BaseDO`                   | `ConfigItemDO`                   |
| 对外 DTO          | `XxxDTO`                                 | `ConfigItemDTO`                  |
| 写操作入参        | `XxxCreateCommand` / `XxxUpdateCommand`  | `ConfigItemCreateCommand`        |
| 查询入参          | `XxxQuery`                               | `ConfigItemQuery`                |
| 业务 API          | `XxxTemplate`                            | `CacheTemplate`                  |
| SPI 接口          | `XxxProvider`                            | `CacheProvider`                  |
| Provider 实现     | `{Impl}XxxProvider`                      | `CaffeineCacheProvider`          |
| Provider 配置     | `{Impl}XxxConfig`                        | `CaffeineCacheConfig`            |
| 顶层配置          | `XxxProperties`                          | `CacheProperties`                |
| Template 实现     | `DefaultXxxTemplate`                     | `DefaultCacheTemplate`           |
| AutoConfig        | `XxxAutoConfiguration`                   | `CacheAutoConfiguration`         |
| Binder AutoConfig | `{Impl}XxxAutoConfiguration`             | `CaffeineCacheAutoConfiguration` |
| Gateway 接口      | `XxxGateway`                             | `ConfigItemGateway`              |
| Gateway 实现      | `XxxGatewayImpl`                         | `ConfigItemGatewayImpl`          |
| Service           | `XxxService`（直接 `@Service`，无 impl） | `ConfigItemService`              |

---

## Command、Query 与分页契约

| 入参语义          | 命名与位置                                            | 示例                                                           |
|-------------------|-------------------------------------------------------|----------------------------------------------------------------|
| 写操作            | `.client.command.<业务对象><动作>Command`             | UserCreateCommand、FileDeleteCommand、TransferTaskRetryCommand |
| 分页查询          | `.client.query.<业务对象>PageQuery`                   | UserPageQuery、MerchantPageQuery                               |
| 非分页集合查询    | `.client.query.<业务对象>ListQuery`                   | XxxListQuery                                                   |
| 树 / 特定条件查询 | `.client.query.<业务对象><条件>Query`                 | PermissionTreeQuery、DictionaryTypeCodeQuery                   |
| 仅 ID 查询        | 复用 `io.github.loadup.commons.request.query.IdQuery` | 详情、按 ID 读取                                               |

- Command 表达写入意图，Query 只读；删除、锁定、重试等操作使用对应 Command，不能使用 IdQuery。新增业务入参不使用 Request
  后缀；HTTP 客户端、上传和第三方协议的技术 Request 类型除外。
- Controller 与 Facade 复用同一份业务入参。Command 直接包含 id、关联编码和业务字段，不创建仅包裹 command 的 Request，也不把
  Command 已包含的 id/typeCode 再作为独立参数传入。业务验证、权限及可信租户解析仍在各自负责层执行。
- Facade 分页统一返回 `io.github.loadup.commons.result.PageDTO<T>`；Controller 通过 `PageResponse.of(page)` 返回
  `io.github.loadup.commons.result.PageResponse<T>`。不新增 FilePageDTO、MerchantPageDTO 等重复分页类型，不在 Facade 返回
  HTTP envelope。
- HTTP 分页结构固定为 `{result, data: [...], pageInfo: {...}}`；pageInfo 使用 totalCount、pageIndex、pageSize，页码从 1
  开始。data 为当前页数组，不嵌套 items/records、total、page、size。前端同步消费顶层 pageInfo。
- 领域 Gateway 统一返回 commons.domain.PageResult<T>；不再新增 DictionaryPage、ContractPage 等业务分页容器。app 使用
  PageResult.map (converter::toDTO) 和 PageDTO.of (result) 保留元数据，转换项由 Spring MapStruct Converter 负责。展示 DTO
  转换使用 `page.map(converter::toView)`，保留元数据，再交给 PageResponse.of。纯分页入参直接复用
  commons.dto.PageQuery；带筛选字段的业务 Query 保留扁平 page/size，并委托 PageQuery.normalized 校验，不重复实现默认值和上限。普通
  SuccessResponse 不含分页字段或 ofPage 工厂。
- 入参、DTO 与分页响应遵循 OpenAPI 和 JSON toString 规范；非分页成功响应使用 SuccessResponse，无数据使用
  SuccessResponse.success，失败使用 FailureResponse。

---

## 包命名（根包：`io.github.loadup.modules.{mod}`）

| 层                | 包路径                       |
|-------------------|------------------------------|
| client DTO        | `.client.dto`                |
| client Command    | `.client.command`            |
| client Facade     | `.client.facade`             |
| client Query      | `.client.query`              |
| client SPI        | `.client.spi`                |
| domain model      | `.domain.model`              |
| domain gateway    | `.domain.gateway`            |
| infra DO          | `.infrastructure.dataobject` |
| infra Mapper      | `.infrastructure.mapper`     |
| infra GatewayImpl | `.infrastructure.repository` |
| infra Converter   | `.infrastructure.converter`  |
| app Service       | `.app.service`               |
| app AutoConfig    | `.app.autoconfigure`         |

---

## MapStruct 规范

所有对象映射统一使用 MapStruct，且只允许 Spring 组件模式，不允许 `default`/instance 模式
（`Mappers.getMapper(...)`、手动 `new XxxConverterImpl()`）。

- 共享配置统一使用 `loadup-commons-dto` 的 `LoadUpMapStructConfig`
  （`io.github.loadup.commons.mapping`），固定配置：
  `componentModel = "spring"`、`unmappedTargetPolicy = ERROR`、`unmappedSourcePolicy = WARN`
- converter 声明一律为 `@Mapper(config = LoadUpMapStructConfig.class)`，
  不在 `@Mapper` 上重复写 componentModel/policy
- 需要模块内静态映射方法时用 `uses = XxxSupport.class`（如 `AuditMappingSupport`）
- converter 通过构造器注入使用方，禁止字段注入

## API 暴露方式

本地业务接口使用 Spring MVC Controller。JSON Controller 调用统一使用 POST + JSON 请求体；上传可以使用
multipart，下载可以返回流式二进制。`loadup-components-webmvc` 对 API 响应统一封装 `result`、`data` 并处理错误及 Jackson 规则；
`loadup-commons-dto` 定义报文类型。资源端令牌校验由 `loadup-components-resource-server` 装配，方法授权由
`loadup-components-authorization` 提供；UPMS 凭证校验和令牌签发由 `loadup-modules-upms-authserver` 对接。

## Vue 3 前端

- 新增或修改 Vue 代码使用 Composition API，优先单文件组件 `<script setup lang="ts">`、`ref`/`reactive`、`computed`
  、composable，以及适用场景下的类型化 props/emits。
- 遵循项目采用的 Vue 3 和 Element Plus API。不引入 Vue 2 语法或弃用 API，包括 `new Vue`、新页面使用 Options API、filters、
  `.sync`、`v-on.native`、`this.$set`/`this.$delete`。

---

## 测试规范

- 集成测试类名 `*IT.java`，单元测试类名 `*Test.java`
- 集成测试用 Testify + Testcontainers（`@EnableTestContainers(ContainerType.POSTGRESQL)`）
- 测试模块 `*-test/` 的 parent 指向根 `loadup-parent`，不是模块聚合 pom
- 测试模块必须有 3 个 yml 文件：`application.yml`（激活 test profile）、`application-test.yml`（本地）、`application-ci.yml`
  （CI）

---

## 技术栈速查

- Java **25** | Spring Boot **4.1.1** | Spring Data JDBC（版本由 Spring Boot 管理）
- PostgreSQL | Caffeine（本地）| Redis/Redisson（分布式）
- Resilience4j **2.3.0**（容错；版本由 loadup-dependencies BOM 统一，跟随 Spring Cloud 2025.1.x）
- JUnit **6.0.3**（由 Spring Boot BOM 管理）+ `loadup-testify-spring-boot-starter` + Testcontainers
- SpringDoc + Scalar | Spotless (Palantir Java Format) | Maven
- License: **Apache-2.0**

---

## 关键参考文件

| 文件                                                          | 用途                     |
|---------------------------------------------------------------|--------------------------|
| `loadup-components/data/loadup-components-database/README.md` | JDBC 配置、ID 与租户约定 |
| `loadup-modules/loadup-modules-upms/ARCHITECTURE.md`          | 业务模块 COLA 分层参考   |

---

## 文档策略

### 三层文档体系

| 位置                           | 受众           | 内容                                     | 维护者      |
|--------------------------------|----------------|------------------------------------------|-------------|
| `AGENTS.md`（根目录）          | AI 编码助手    | 项目定位、禁止项、构建命令、技术栈       | 开发者 + AI |
| `**/README.md`（模块级）       | 集成方开发者   | 模块用途、Maven 坐标、配置方式、示例     | 模块开发者  |
| `**/ARCHITECTURE.md`（模块级） | 深入理解者     | 设计决策、内部结构、扩展点               | 模块开发者  |
| `docs/`                        | 外部文档站读者 | Hugo + Hextra 文档站，面向非本项目开发者 | 文档维护者  |

### 规则

1. **docs/ 不作为编码依据。** docs/ 是 Hugo + Hextra 文档站源码。AI 写代码时以 `AGENTS.md`、模块 `README.md`、
   `ARCHITECTURE.md` 和实际代码为准。

2. **docs/ 不参与 Java/Maven 构建。** docs/ 目录是独立的 Hugo 项目，有自己的构建流程（`hugo` CLI）。CI 中不需要为它配置 Maven
   插件。

3. **模块 README.md 是集成方入口。** 说明用途、Maven 坐标、配置及接入示例。

4. **ARCHITECTURE.md 记录设计。** 说明职责边界、内部结构、关键决策和扩展点；遵循项目每个模块维护 README 与 ARCHITECTURE
   的文档结构。

5. **文档与代码同步。** 修改代码导致接口、配置或行为变化时，同步更新对应的 README.md 或 ARCHITECTURE.md；使用根目录的
   `doc-sync-agent.py` 同步到文档站。首页由文档站单独维护，不由同步脚本生成。

6. **根 README.md 是项目门面。** 面向首次接触者，含项目简介、快速开始、BOM 引入方式、模块目录概览，不深入单个模块细节。

## 日志、观测与任务收尾

- 框架日志打印统一使用 `loadup-commons-log` 的 `LogUtil`，生产代码显式传入来源类；第三方要求 SLF4J Logger 时通过
  `LogUtil.getLogger` 获取。保留参数化占位符，异常作为最后参数，敏感字段显式脱敏。
- 指标和追踪使用 Spring Boot 管理的 `MeterRegistry`、`ObservationRegistry` 和 Micrometer Tracing；组件注入共享实例，基础
  HTTP/JVM 观测由 Boot 提供。自定义指标以 `loadup.<domain>.*` 命名，仅使用低基数标签，配置使用标准 `management.*`。
- 每完成一项任务立即更新根 `ROADMAP.md`：移除已完成的实现项，把未执行的测试或部署验收单独保留为待办；源码或测试已编写不等于运行验证通过。

## 执行链共享数据

- 业务元数据使用 `commons-context` 的不可变 ExecutionContext 与 ScopedValue ContextHolder；入口用
  runWith/callWith，下游只读，临时覆盖派生新对象进入嵌套回调。ServiceTemplate 的 init/clean 按入口及资源需求使用，普通
  Service 可直接调用。
- 跨线程业务上下文用 Observability 的 LoadUpContextTaskDecorator 或纯 Java wrap；Boot 标准 Micrometer 装饰器负责 Trace，任意
  Micrometer snapshot 不会自动捕获 ScopedValue。只绑定轻量不可变值；用户身份由 SecurityContextHolder 管理，traceId/spanId 由
  Micrometer/MDC 管理。

## Business Facades and Persistence

- Public business interfaces reside in `client.facade` and use `XxxFacade`. Application services implement the
  interfaces directly; Controllers and cross-module consumers depend on Facades. Consumer-supplied extensions remain
  `client.spi.XxxProvider` or `XxxHandler`.
- Facades return client DTOs or JDK values. HTTP envelopes belong to Controllers. Keep domain Gateway ports separate
  from public Facades.
- Persistence stays in infrastructure. Use Spring Data JDBC for suitable simple aggregates and parameterized Spring
  JDBC for conditional writes, locking and complex queries. Keep MapStruct converters separate and Spring-managed.
- Repository reads resolve the trusted tenant through `TenantContext.tenantId()` and expose tenant-free methods; never
  accept a tenant identifier as a repository/query argument. Explicit SQL binds the same context value.
- Explicit SQL maintains ID, tenant, audit and soft-delete fields; use the shared `IdGenerator` and injected `Clock`
  where applicable. Tenant-scoped reads and writes include a trusted tenant predicate. Check affected rows for state
  and version updates.
- Business writes and reliability records use the same Spring-managed data source and JDBC transaction manager. Sort
  SQL uses fixed, whitelisted columns; schema changes target PostgreSQL and preserve business constraints.

## Mapping, HTTP Contracts and Utilities

- Every object mapping uses a Spring-managed MapStruct Converter with `@Mapper(config = LoadUpMapStructConfig.class)`
  and constructor injection. DTO/domain mapping belongs to app.converter, DO/domain mapping to infrastructure.converter,
  HTTP projections to web converters. Do not use Mappers.getMapper, static conversion factories or manually instantiate
  generated converters.
- Domain methods implement business validation and transitions, not representation conversion or input-field copying.
  Express update merge rules in Converter mappings; document null-as-unchanged versus empty-as-clear. Authorize
  private-field modifications before mapping.
- Include OpenAPI Tag/Operation on public Controllers and Schema descriptions/examples/units/formats on public DTO,
  Command and Query fields. Validation annotations define actual constraints; documentation must agree. Java
  descriptions are English. Credential request fields are WRITE_ONLY with synthetic examples. Document HTTP 200 business
  failures and actual response envelopes. Domain has no OpenAPI annotations.
- Business JSON Controllers explicitly return IResponse implementations; select SuccessResponse, PageResponse or
  FailureResponse according to the Command/Query and pagination contract above. Global exception handling returns
  FailureResponse. Downloads, SSE and standard protocol endpoints preserve their protocol-specific types. Keep
  application services free of HTTP envelopes.
- Project DTOs, commands, queries, domain data, DOs and envelopes implement JSON toString through
  ToStringAsJson.reflectionToString. Credentials and keys must be redacted. Output handles records, inheritance, cycles
  and bounded collections without triggering resource access. Preserve JDK/third-party types and value objects with
  intentional textual semantics. Log JSON and HTTP JSON have separate disclosure policies.
- Prefer JDK APIs, then Guava for missing general utilities and Vavr for useful Either/Validation/composition. Public
  contracts use JDK types. Avoid pass-through utility wrappers; retain project-specific money, signature, context and
  masking semantics. Propagate Vavr Try failures across transactional entry points. Manage versions through BOM and
  declare dependencies where used.
- Business configuration uses loadup.modules.<module>.*. Optional web exposure is selected by the web dependency; do not
  add general web.enabled properties. Keep meaningful capture/path configuration.

## Input, time and failure contracts

- New password inputs share UPMS `@ValidPassword` and `PasswordPolicy`: at least 8 Unicode code points and at most 72
  UTF-8 bytes, matching BCrypt's byte limit. Create, register, change and reset entry points validate before encoding or
  persistence; login accepts existing credentials without applying a new-password policy.
- Public business pagination defaults to page 1 and size 20; explicit invalid values are rejected and size is capped at
    100. Sort fields use declared whitelists and fixed SQL columns; never pass user input to SQL ordering.
         Audit Controller and Facade share AuditPageQuery; trusted tenant identity is supplied by the application layer.
- JDBC writes explicitly maintain persistence createdAt/updatedAt. Business event and expiry timestamps use injected
  Clock with
  Clock.systemDefaultZone () defaults and convert using clock.getZone (). Each module qualifies its named
  `<module>Clock`; infrastructure lease clocks are separately qualified. Verification-code createdAt is an in-memory
  business timestamp and must still be initialized.
- Expected business failures use CommonException with stable ResultCode (PARAM_ILLEGAL, NOT_FOUND, CONFLICT,
  INVALID_STATE, RATE_LIMITED or authentication codes). Unexpected infrastructure failures remain system failures. API
  exception advice preserves result codes and HTTP 200 envelopes; non-API and Spring Security protocols retain their
  responsible handlers.
- Logs contain neither raw verification codes, credentials nor raw JSON payloads. Mask email/phone explicitly. Jackson
  exceptions may contain payload fragments: log operation and exception type without the exception message or chain.
- ArchitectureTest checks bytecode dependencies; SourceStandardsTest checks
  source-retained MapStruct/Lombok annotations. Run focused regression tests when changing these contracts.

- Every loadup-parent declaration includes an explicit relativePath to the root pom.xml; Maven's default ../pom.xml can
  select an unrelated aggregator and silently fall back to an installed stale parent.

- Commons dependency-cycle gates group classes by Maven artifact ownership, not arbitrary Java package names. Framework
  WebMVC error adapters are valid HTTP adapters. Container-test naming is also checked from source so optional test
  modules absent from the launcher classpath are still covered.

- 默认时间契约：JVM、业务 Clock、数据库审计字段及 JSON 无偏移日期使用系统本地时区。Clock 默认 `systemDefaultZone()`，转换使用
  `clock.getZone()`；数据库会话时区与应用本地时区保持一致。JWT、请求签名的 epoch 时间戳及显式偏移的协议时间保持绝对时间语义。

## ResultCode 与错误码规范

- 基础契约由 loadup-commons-error 提供，生产代码仅依赖 JDK；需要抛出规范错误的模块直接声明该依赖。ResultCode/CommonException
  包名保持 commons.result/commons.error。
- 业务错误统一在 client.error.XxxResultCodeEnum 声明，枚举实现 ResultCode；码为 `<MODULE>_<REASON>`，大写字母/数字/下划线，最长
  64。固定英文文案最长 256，字段名或异常消息不作为动态错误码。已发布码保持稳定，文案调整不重命名码。
- 使用 CommonException (resultCode) 或 CommonException (resultCode, cause)；业务生产代码不构造裸
  RuntimeException/IllegalArgumentException/IllegalStateException/UnsupportedOperationException。CommonException 作为唯一
  unchecked 基础类保留事务回滚语义。技术组件可保留其识别类型，分类枚举必须实现 ResultCode。
- Result 是不可变报文快照，不实现 ResultCode。FailureResponse/Result 的失败工厂仅接收枚举，文案不由
  String/exception.getMessage 覆盖。SDK/JDK 前置条件和启动校验、第三方协议异常保留标准负责层语义；外部 JSON API 映射为规范码。
- client 直接引用本服务 domain 中的共享领域枚举，或使用 String；避免在 client 重复定义领域枚举。
- 只将固定枚举码及文案放入失败响应。cause 保留用于诊断；日志遵循敏感信息策略，异常链不进入响应。CommonException 的 JSON
  toString 是底层无 JSON 依赖的限定例外，仅输出 code/message。
- 新增错误枚举后运行 ErrorCodeContractTest、架构/源码门禁及相关 MVC 回归。真实数据库和外部系统验收单独记录。

## Scheduler PowerJob binder integration

- PowerJob integration belongs to scheduler-binder-powerjob; do not create a standalone LoadUp PowerJob artifact. Use
  the official BasicProcessor, TaskContext, ProcessResult and PowerJobClient with native powerjob.worker/client
  properties. Keep Server separate. Native capabilities remain native; Scheduler binders only wake registered
  ScheduledTask scanners and do not translate platform instance models or maintain job-name ID mappings.
- Spring BasicProcessor.process receives an isolated ScopedValue and a shared-registry Observation. Metrics use
  processor/outcome only; reconstruct trusted business identity explicitly from persisted records.
- Durable business queues use transactional enqueue plus conditional claims. Transfer permits QUEUED → RUNNING only;
  failed-task requeue is explicit. Worker-crash recovery requires lease/reconciliation design, never unconditional
  reclaim of RUNNING tasks.
- Scheduler tests must not launch a standalone PowerJob Server process or download its executable jar. Keep binder
  configuration, dispatch, context and observation tests in the binder; real-platform acceptance runs against a
  separately provisioned environment.

## Scheduler and DelayedTask

- For deadline tasks read Scheduler and DelayedTask README/ARCHITECTURE. Scheduler uses one Provider binder (local
  Spring @Scheduled or PowerJob); it only wakes scanners. Do not use @Async as a timer or create a remote engine job per
  business record.
- DelayedTask owns bizId/executeAt, transactional registration, conditional claims, leases and retries. Register within
  the business transaction on the same DataSource. Expired-lease recovery is at-least-once: handlers must be idempotent;
  token fencing protects task acknowledgements, not external side effects. XXL-JOB/JobRunr binders are not implemented.

## 应用间 Dubbo Triple 与系统时区

- LoadUp Pay 的应用间调用统一使用 Dubbo Triple；不新增内部 HTTP 客户端、内部 REST 入口或 HTTP 回退，也不保留
  gRPC 契约；Protobuf 仅用于 IDL。商户/运营 API、收银台、渠道通知与外部系统沿用各自协议。
- 每个提供方在自己的 `<service>-client` 模块维护 Java Facade、Command/Query/DTO、结果码，以及 Protobuf
  `.proto`、生成存根和 Wire↔DTO mapper；IDL 代码固定在 `rpc` 子包。`*-adapter` 用 `@DubboService` 实现生成的
  `ImplBase`，`*-infrastructure` 用 `@DubboReference` 引用生成的接口。domain 不依赖 client，app 不直接引用 rpc
  类型，跨服务只复用源服务的 Wire 类型。
- 通用数据契约放现有 `loadup-commons`，通用 RPC 支持放现有 `loadup-components-rpc-binder-dubbo`
  （`RpcTokenContext`、`RpcExceptionCodes`、provider filter）与 `loadup-components-rpc-proto`（Wire 标量转换）；commons 不反向依赖
  components，不另建
  pay-common/pay-rpc。
- 跨进程只传 Protobuf 消息，使用 Dubbo Triple 的 IDL 分支，不产生 Wrapper 信封。`*-client` 用 `dubbo-maven-plugin`
  生成消息与存根；MapStruct mapper 负责 Wire↔record DTO，`RpcScalarMapper` 处理 `Timestamp`/`Instant`/`BigDecimal`。
  字段号不可复用，删除字段必须 `reserved`，字段改名/删除都是破坏性变更。不需要 `serialization`、
  `serialize-check-status` 或 `check-serializable` 配置。
- 目标地址/超时/重试/TLS 通过 `dubbo.*` 配置绑定。K8s 命名固定 `<service>-headless:50051`，同 Namespace 使用
  `tri://<service>-headless:50051`，不把环境地址写进 Java；`registry.address: N/A`，写调用
  保持 `retries: 0`，RPC 端口只对同 Namespace 开放。
- 消费方用 `RpcTokenContext.call(token, ...)` 逐次传播令牌，调用结束即移除；提供方 filter 重建
  `Jwt`/`SecurityContext` 并恢复。入口负责签名、issuer、audience 和 token_use，内部跳转只校验 `exp`；租户来自
  已验证身份。SERVICE token 逐次读取，商户委托遵循受众约束。业务拒绝编码为 `loadup-result-code=<CODE>`，消费方
  只按本地白名单映射；未知远端错误映射为依赖失败，不输出凭证/载荷。网络调用不跨本地事务，写操作保留幂等键。
- 业务时间采用 `ZoneId.systemDefault()`、`Clock.systemDefaultZone()` 或已注入 Clock 的 zone；不在代码、JVM 参数、
  JDBC 初始化 SQL 或 K8s 清单强制指定时区。JWT epoch、`Instant` 等协议边界保持绝对时间语义。
- 修改 RPC 契约、认证或部署时，读取 [RPC 规范](design/RPC.md)；源码编译、测试执行与真实 RPC 验收分别报告，遵守
  现有窄范围 clean 构建约定。
