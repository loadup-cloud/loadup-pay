# Spring Data JDBC 迁移盘点（进行中）

本文件记录迁移初期的静态搜索快照，不代表 P0 盘点已完成；后续已删除的引用仍可能列在快照中。搜索工作空间的非 Markdown 源码、POM
与配置，排除 target 和 .git；依赖树、外部消费者及动态生成产物尚未核实。

## 当前核查补充（覆盖下方历史快照）

- 已完成 Spring Data JDBC Repository 集成：`loadup` 根包在 Boot 单次 Repository 扫描前注册为自动配置包；框架与 Pay 的简单单表读取改为 Repository 默认方法，复杂条件、行锁、批量与状态围栏仍使用显式 Spring JDBC。Pay 的 `customer`、`product`、`realtime`、`limitcenter`、`payment` 及框架的 UPMS、Merchant、Contract、Dictionary、File、Notification、Transfer、Gotone、DFS 已增加 Repository 并替换普通按键读取。主源码与测试源码已通过框架和 Pay 的 `clean test-compile`；测试用例、数据库执行与装配运行未验证。

- 租户条件不再由调用方传入 Repository 查询。`TenantContext.tenantId()` 从 `DatabaseTenant` 解析可信租户；Repository 对外暴露 `findById`、`findByUsername`、`findByOrder` 等无租户参数方法，内部派生查询使用上下文租户。显式 SQL 的租户绑定也改为从 `TenantContext` 读取。调用方传入的租户参数仅保留在既有 Gateway/Facade 签名中，不再作为查询值；跨租户维护扫描（如 Transfer 后台领取、File 待清理扫描）继续使用显式跨租户查询。

- `BaseDO.deleted` 已从 `Integer` 改为 `Boolean`，与 PostgreSQL `BOOLEAN` 列和 Spring Data JDBC 映射一致；MapStruct 中 `deleted` 常量同步改为 `false`。该变更尚未在数据库执行验证。
- 已将当前九个带 `@Table` 的 Spring Data JDBC record 的显式 `@Column` 名称与对应初始表静态比对，未发现不存在的列；此检查不验证类型、映射、SQL 生成或数据库执行。

- Customer 商户关系及授权的按 ID 与业务键普通读取使用 Spring Data JDBC，先校验可信租户并过滤软删除；业务键仅查询 INIT/ACTIVE，行锁及条件写入仍使用 Spring JDBC。测试源码覆盖行模型转换及租户不匹配的拒绝；测试、装配和数据库读取尚未运行验证。

- File、Audit、DFS 和 Gotone 的聚合模板消费者声明在 Boot Data JDBC 自动配置之后；Gotone 默认存储要求存在单数据源候选。实际 Bean 装配尚未运行验证。

- Gotone 的服务与渠道配置读取使用 Spring Data JDBC，包含服务编码、启用、软删除和当前租户（含 NULL）条件，渠道按优先级排序；通知记录逐接收者显式 insert。JSON 字面量 null 按空配置处理；已补充 PostgreSQL 集成测试源码，覆盖跨租户配置、排序及记录写入，新装配和读写尚未运行验证。

- Framework 数据库 DFS 的文件新增与普通读取使用 Spring Data JDBC 独立行模型，查询保留租户（包括 NULL）及软删除条件；软删除更新仍使用条件 SQL。旧 FileStorageDO 已移除，相关测试源码改用直接插入测试数据。BYTEA 映射、装配和运行行为尚未验证。

- File 与 Audit 的 Spring Data JDBC 行模型对全部列显式指定 PostgreSQL 小写名称，避免依赖带引号标识符的默认命名策略；实际数据库映射仍未运行验证。

- Framework File 的单行资源与引用新增使用 Spring Data JDBC `JdbcAggregateTemplate.insert`，显式插入应用生成的 ID 和布尔删除标记；普通读取、资源分页、待清理扫描及引用列表、计数使用 Spring Data JDBC，父记录行锁、按租户与业务键删除引用及条件状态变更仍使用 Spring JDBC。测试源码覆盖资源插入与读取转换；新装配及读写尚未构建或运行验证。

- Outbox 与 DelayedTask 的静态领取路径均使用短事务和 SKIP LOCKED，确认更新匹配 token 与未过期租约；Outbox 配置限制租约毫秒转微秒时不能溢出。真实并发、锁与恢复行为仍未验证。

- Framework File 调用 DFS 上传、下载及清理时绑定对应资源租户；流式下载异步线程不再依赖原请求线程的租户上下文。新增测试源码覆盖下载租户传递与上下文恢复；数据库 DFS 的 content_type 长度与 File 的 255 字符限制一致。测试、实际装配、迁移执行和异步下载尚未运行验证。

- Framework 主源码中 24 条可静态识别且能匹配 Flyway 表的 INSERT 列清单，与 34 张可识别初始表的列名比对后未发现缺失列；testify 示例 `users` 表不在这些初始迁移中。动态 SQL、值绑定、约束和实际执行未由此检查证明。

- Framework File 的父文件锁要求当前线程存在绑定文件数据源的可写事务；引用创建、移除及删除请求均在持锁状态下检查父文件。实际事务代理与锁竞争尚未运行验证。

- 商户本地事实接口从可信上下文校验租户；行锁入口要求当前线程存在绑定商户数据源的可写事务，避免锁在关联写入之前失效。事务装配和锁行为尚未运行验证。

- Framework Merchant 的资料更新要求新版本为预期版本加一，并在条件中匹配不可变的商户编码；零行仍映射为冲突。实际数据库并发和更新尚未验证。

- 工作空间非 Markdown 源码、构建文件和配置中搜索 Flex/MyBatis 标识（排除 target 与 .git）：Pay 的架构边界测试保留禁止引入旧依赖的规则；其他位置未发现匹配。工作空间消费者 loadup-application 的旧 Flex 专用规则已移除，其 domain 禁止依赖 Spring 的规则仍保留。该搜索不覆盖外部消费者、依赖树或动态生成产物。

- Customer 的关系及操作授权创建允许 INIT 版本 0 或 ACTIVE 版本 1；更新通过版本、原有身份和有效期以及状态转换条件限制写入。新增测试源码覆盖创建、撤销和版本校验；测试及 PostgreSQL 执行均未运行。

- 已将 Pay 七个服务主源码中可静态识别的 ON CONFLICT 列目标与各自初始脚本中可识别的主键和唯一索引比对，未发现不匹配；动态语句及数据库执行仍待验证。

- 对账审核主记录、提案和事件 INSERT 的命名参数已与数据对象 getter 和公共字段静态比对，未发现缺失属性；驱动绑定与事务行为仍待验证。

- 对账输入明细与差异记录批量 INSERT 的命名参数已与数据对象 getter 及公共字段静态比对，未发现缺失属性；驱动绑定仍待验证。

- 对账导入、验证请求和批次 INSERT 的命名参数已与数据对象 getter 及继承公共字段静态比对，未发现缺失属性；驱动绑定尚未验证。

- 对账单请求 INSERT 的命名参数已与数据对象 getter 及继承的公共字段静态比对，未发现缺失属性；数据库绑定行为仍未验证。

- 对 Pay 七个服务中可静态识别的 INSERT 列清单与对应初始表定义进行了名称比对，未发现缺失列；动态 SQL、参数类型和约束仍需单独核查。

- 对账批次更新在写事务中锁定原记录、校验状态转换及预期版本；SQL 再按租户、ID、版本和未删除条件更新，零行返回并发错误。实际锁和竞争行为尚未验证。

- 对账输入明细按最多 500 行分批写入，检查结果数组长度及每行计数，仅接受 1 或 SUCCESS_NO_INFO；真实驱动返回值和失败回滚仍待验证。

- 对账差异批量写入检查返回数组长度，并仅接受每行影响数为 1 或 JDBC SUCCESS_NO_INFO；失败时抛出并发错误。驱动实际返回和事务回滚尚未验证。

- 对账输入清单在锁定读取与主记录写入前验证数据源事务；差异批量写入先通过批次锁定路径验证事务。实际回滚及锁行为尚未验证。

- 对账导入、对账单、审核事件和验证请求的请求键冲突目标与各自的 (tenant_id, caller_service_id, request_key) 唯一索引一致；其他唯一约束和真实并发异常仍需数据库验证。

- 对账差异记录的两处实体查询投影覆盖数据对象业务字段，包括全部五个 Boolean 差异标志；尚未验证驱动转换与数据库执行。

- 对账批次和输入清单的数据对象业务字段已与查询投影静态比对，未发现字段遗漏；锁和事务行为仍需数据库验证。

- 对账审核主记录、提案和事件的显式查询投影已与各自数据对象业务字段静态比对，未发现字段遗漏；实际数据库结果映射与并发行为尚未验证。

- 对账导入与验证请求的数据对象业务字段已与各自显式查询投影静态比对，未发现字段遗漏；字段转换、数据库约束与实际运行仍待验证。

- 对账单请求的两处 JDBC 查询投影已与 ReconciliationStatementDO 的业务字段比对，包含 request_sha256、source_sha256 和 proof_sha256；实际驱动映射仍待数据库验证。

- 对账明细 reconciliation_entry 的查询投影已与当前数据对象的业务字段和初始表结构逐项比对；共享审计字段包含在查询中，布尔 deleted 不进入整数属性映射。其他对账表仍需逐项核查及数据库验证。

- Framework 与 Pay 的 POM、properties、YAML 和自动配置 imports 中静态搜索未发现 MyBatis、iBATIS 或旧 database-processor 引用；该检查不能代替 Maven 依赖树。

- UPMS 测试模块声明了 infrastructure 与 database 测试依赖；其 SQL 初始化引用的 UPMS 和 sequence 迁移文件均存在于对应模块主资源中。测试 classpath 的实际解析与脚本执行尚未验证。

- UPMS 测试建表脚本包含主要用户、角色、权限、关联和 OAuth 表，但与生产迁移并不完全相同：此前生产包含 user_social、password_reset_token 和 user_config 而测试包含旧 operation_log；测试建表内容现已与生产初始迁移对齐。测试脚本不能替代生产 Flyway 验证。

- Gotone 和 DFS 的初始迁移版本已分别调整为 V20261002000005 和 V20261002000006；下方旧文件名属于盘点时的历史快照。UPMS 测试配置同时关闭 Boot 与 LoadUp 的 Flyway，使用显式测试建表脚本。

- 通知收件箱写入以 (tenant_id, recipient_id, request_key) 为冲突目标，返回实际插入行数；已读与归档路径区分条件更新和已存在记录。尚需验证并发和事务行为。

- DelayedTask 的手写行映射未读取 deleted 布尔列；GlobalUnique 的租户解析在单租户模式使用默认租户，多租户必需模式拒绝缺失上下文。Pay 主源码静态搜索未发现对已检查布尔列使用 ResultSet.getInt。此证据不覆盖全部字段。

- acquire、payment、limitcenter、product、realtime 的基础设施主源码中，未发现同一文件同时使用 BeanPropertyRowMapper 和 SELECT *；仍须逐个核对显式列、转换和 PostgreSQL 驱动行为。

- 对账 Gateway 的实体查询已改用显式列清单，不再把 PostgreSQL 布尔 deleted 映射到 BaseDO 的整数属性；差异标志使用 Boolean 属性。结果读取和 SQL 列清单尚未经过数据库验证。

- 跨 Framework 与 Pay 主资源目录静态核对了 Flyway 版本，未发现重复版本；自动配置 imports 中属于工作空间的类名均能在主源码中找到。此检查不覆盖依赖可解析性或运行时装配。

- UPMS PostgreSQL 表结构已加入基础设施的 Flyway 初始迁移；独立 schema.sql 中的本地初始化账号和授权数据不随迁移自动写入。尚未执行 Flyway。

- 当前静态核查：customer、product、limitcenter、realtime、acquire、payment、reconciliation 的主资源目录各有一份初始 SQL；这些脚本未发现 MySQL 的 ENGINE、TINYINT、DATETIME、AUTO_INCREMENT、INSERT IGNORE 或 FOREIGN KEY 语法。该检查不证明 SQL 可执行或业务约束完整。

- Framework 与 Pay 生产 Java 中已静态搜索不到 Flex API、生成 Mapper 或 TableDef 引用；Pay 的边界测试保留禁止依赖规则。已清理
  Framework 的 Flex BOM、注解处理器、旧监听器与相关自动配置引用。
- Framework UPMS 六个 Gateway、Gotone JDBC store 和相关初始表结构已改为 JDBC/PostgreSQL；UPMS
  的用户、角色和部门关系写入与删除增加事务内锁定及引用检查。默认租户及缺失租户行为由数据库组件的 DatabaseTenant 明确解析。
- 数据库核心配置提供数据库时钟和 JDBC 事务管理；审计事件追加使用 Spring Data JDBC 显式 insert，字典父子记录创建与类型删除使用同事务锁和复核。
- 此项仅是源码变更和静态搜索结果，不表示所有模块的租户、Clock、状态围栏和关联治理已经审查完成。未执行构建、测试或数据库运行验证；历史快照及逐入口清单仍需逐项更新。

## 历史状态记录

- 已将 sequence 分配入口改为 Spring JDBC，并删除该入口的 Flex 数据对象和 Mapper 扫描。启动装配、并发及回滚未运行验证。
- GlobalUnique 已使用 Spring JDBC、按业务唯一键指定冲突目标，并删除自身 Flex 数据对象和注解处理器配置；实际事务、并发和约束行为未运行验证。公共
  ID 生成器与数据库自动配置仍依赖 Flex，属于后续迁移范围。
- DelayedTask 的注册、领取、确认和重排路径已改为 Spring JDBC，领取使用短事务和 `FOR UPDATE SKIP LOCKED`；其数据对象暂时继承仍有
  Flex 耦合的 BaseDO。迁移后的装配、数据库并发、回滚与锁行为尚未运行验证。
- Customer 的商户关系和操作授权读写已改为 Spring JDBC，保留租户过滤、行锁、版本条件与部分唯一索引；商户资料改经 Framework
  Merchant 的本地 client 接口读取与锁定。Merchant 持久化已改为 Spring JDBC，但共享 BaseDO 仍有 Flex
  耦合；新库脚本使用布尔删除标记。相关并发、事务与装配未运行验证。
- Framework File 的资源和引用访问已改为 Spring JDBC；删除外键后，创建引用与请求删除均在事务内锁定父文件并检查状态或引用。实际锁竞争、补偿和自动装配尚未运行验证。
- Framework Transfer 已改为 Spring JDBC，任务与选项同事务写入，领取和状态更新显式限制租户与当前状态；后台队列扫描和按 ID
  启动查询仍是内部跨租户入口。移除选项外键及 Flex 类型后，装配、并发和回滚尚未运行验证。
- Framework Contract 的目录、合约和修订已改为 Spring JDBC；合约及修订在调用方事务内写入，请求键与业务范围冲突分别识别。Product
  的合约锁与修订读取改经本地 client 接口；装配、约束和并发重放未运行验证。
- Product 准入记录及策略发布已改为 Spring JDBC，业务唯一键冲突与其他数据库错误分开处理，策略发布保留租户、版本围栏与事务外有限重试。数据对象仍继承带
  Flex 耦合的共享 BaseDO；真实并发、事务、重试及 Outbox 联动未运行验证。
- Limitcenter 的策略、额度桶、操作门闩、预留及明细、执行授权、退款和结算持久化已改为 Spring
  JDBC，保留租户、行锁、版本及原值围栏；适用的幂等插入使用目标业务唯一键的 `ON CONFLICT`
  。事务仅重试明确识别的业务键竞争与暂时性数据库异常，支付门闩的唯一键竞争按 PostgreSQL 约束名识别。共享 BaseDO 仍有 Flex
  耦合，对应表、事务及并发行为均未运行验证。
- Realtime 投影回执、退款汇总及订单、支付、退款投影与分页已改为 Spring
  JDBC，保留来源版本围栏、商户过滤和业务键竞争处理；事务仅重试明确识别的业务键竞争及暂时性数据库异常。初始脚本中的 JSON
  检查已改为 PostgreSQL JSON 类型校验，软删除改为布尔列；共享 BaseDO 仍有 Flex 耦合，真实数据库、并发和分页行为未验证。
- Payment 路由配置、支付输入、支付动作及关闭证据、退款汇总、支付与退款恢复游标、恢复重试回执以及待恢复事项查询已改为 Spring
  JDBC，保留租户、业务键、退款汇总原值和版本围栏、输入绑定状态版本条件、游标 generation 围栏和重复读下的时间/ID 翻页；支付其他
  Gateway 仍使用 Flex，其共享事务重试仍会捕获所有重复键。支付初始脚本的 JSON 校验改为 PostgreSQL
  类型检查，已迁移表的布尔语义字段改为布尔列；未运行数据库验证。
- Payment 尝试的 Gateway、渠道核验查询及对账读取中对应的尝试查询已改为 Spring
  JDBC，保留请求键、渠道请求查询、版本条件更新及受限批量读取；尝试表的软删除已改为布尔列。对账读取的其他表仍使用
  Flex，尝试表的多个独立唯一约束仍受共享事务的宽泛重复键重试影响。
- Payment 对账采集头及明细已改为 Spring JDBC，采集封存保留行锁、状态和版本条件，明细按 256
  条分批写入并检查批处理结果；分页仍在重复读事务中核对总数、序号及内容键。批量回滚、驱动返回计数及数据库并发尚未运行验证。
- Payment 退款容量的创建、锁定、预留、结果更新和回调/核验读取已改为 Spring JDBC，保留支付 ID
  锁顺序以及版本、已退款和已预留金额围栏；对应软删除改为布尔列。退款请求和结果的其他表仍使用 Flex，资金并发及事务回滚未运行验证。
- Payment 退款请求、退款分配计划、退款渠道回执、退款受理证据、退款结果异常和退款交易声明已改为 Spring
  JDBC，对账中的受理证据、回执和声明读取也已同步；退款请求保留商户退款号唯一键和状态/版本条件，分配计划保持有序读取及子单/支付/金额校验，渠道退款交易号保留跨租户精确查重。退款订单等仍使用
  Flex，结果重试和真实数据库行为未验证。
- Payment 父门闩、支付门闩、控制回执、支付渠道回执、结果异常、渠道交易声明和支付订单的受理、控制、结果更新、对账及恢复读取已改为
  Spring JDBC，保留租户、行锁、业务唯一键、版本/fence epoch 条件和首次未知时间；渠道交易声明按渠道、账户范围和交易号执行跨租户精确查询。已迁移表软删除改为布尔列，退款结果等其他表仍使用
  Flex，共享事务的重复键分类仍待收紧。
- Outbox 和 Inbox 已使用同一注入的 JDBC 事务管理器，领取使用 `SKIP LOCKED`，确认及失败处理检查 token
  和租约；迁移后的装配、并发、回滚与恢复尚未运行验证。
- Framework 数据库模块已声明 Spring JDBC 与由 Boot 管理版本的 Spring Data JDBC starter；ID 接口和共享 Clock 配置已从 Flex
  类型与配置中拆开，但 BaseDO、租户、审计、事务及 Repository 回调等完整替代方案尚未落地。尚未迁移的 Flex 路径仍有过渡配置。新增
  starter 的自动装配尚未运行验证。
- 迁移前须逐个入口记录表、事务、租户、锁、幂等、异常、分页与软删除；下列命中只是待审查线索，不表示已完成行为审查。

## Flex/MyBatis 相关文件（252）

- `loadup-application/src/test/java/io/github/loadup/application/arch/ArchitectureTest.java`
- `loadup-framework/config/quality/spotbugs-exclude.xml`
- `loadup-framework/loadup-commons/loadup-commons-dto/pom.xml`
- `loadup-framework/loadup-commons/loadup-commons-dto/src/main/java/io/github/loadup/commons/dataobject/BaseDO.java`
- `loadup-framework/loadup-components/data/loadup-components-database-processor/pom.xml`
-
`loadup-framework/loadup-components/data/loadup-components-database-processor/src/main/java/io/github/loadup/components/database/processor/LoadUpMyBatisFlexProcessor.java`
-
`loadup-framework/loadup-components/data/loadup-components-database-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
-
`loadup-framework/loadup-components/data/loadup-components-database-processor/src/main/resources/io/github/loadup/components/database/processor/mybatis-flex.config`
-
`loadup-framework/loadup-components/data/loadup-components-database-processor/src/test/java/io/github/loadup/components/database/processor/LoadUpMyBatisFlexProcessorTest.java`
- `loadup-framework/loadup-components/data/loadup-components-database/pom.xml`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/JdbcTransactionAutoConfiguration.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/MyBatisFlexAutoConfiguration.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/autoconfigure/SequenceAutoConfiguration.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/id/IdGenerator.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/listener/BaseEntityListener.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/java/io/github/loadup/components/database/sequence/dataobject/SequenceAllocationDO.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/autoconfigure/JdbcTransactionAutoConfigurationTest.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/autoconfigure/MyBatisFlexAutoConfigurationTest.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/autoconfigure/PersistenceConventionGuardTest.java`
-
`loadup-framework/loadup-components/data/loadup-components-database/src/test/java/io/github/loadup/components/database/listener/BaseEntityListenerTest.java`
- `loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-binder-database/pom.xml`
-
`loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-binder-database/src/main/java/io/github/loadup/components/dfs/database/DatabaseDfsProvider.java`
-
`loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-binder-database/src/main/java/io/github/loadup/components/dfs/database/autoconfigure/DatabaseDfsAutoConfiguration.java`
-
`loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-binder-database/src/main/java/io/github/loadup/components/dfs/database/dataobject/FileStorageDO.java`
-
`loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-test/src/test/java/io/github/loadup/components/dfs/test/TestApplication.java`
- `loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/pom.xml`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/config/GotoneStoreJdbcAutoConfiguration.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/config/JdbcChannelConfigProvider.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/config/JdbcServiceConfigProvider.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/dataobject/NotificationRecordDO.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/dataobject/NotificationServiceDO.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/java/io/github/loadup/components/gotone/store/dataobject/ServiceChannelDO.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-test/src/test/java/io/github/loadup/components/gotone/test/store/JdbcStoreBoundaryTest.java`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-test/src/test/java/io/github/loadup/components/gotone/test/store/JdbcStoreIT.java`
- `loadup-framework/loadup-components/reliability/loadup-components-delayedtask/pom.xml`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskAutoConfiguration.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskStore.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskTemplate.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/dataobject/DelayedTaskDO.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/test/java/io/github/loadup/components/delayedtask/DelayedTaskBoundaryTest.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/test/java/io/github/loadup/components/delayedtask/DelayedTaskIT.java`
- `loadup-framework/loadup-components/reliability/loadup-components-globalunique/pom.xml`
-
`loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/main/java/io/github/loadup/components/globalunique/DefaultGlobalUniqueTemplate.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/main/java/io/github/loadup/components/globalunique/autoconfigure/GlobalUniqueAutoConfiguration.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/main/java/io/github/loadup/components/globalunique/dataobject/GlobalUniqueDO.java`
-
`loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/test/java/io/github/loadup/components/globalunique/GlobalUniqueContractTest.java`
- `loadup-framework/loadup-dependencies/pom.xml`
- `loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/src/main/java/io/github/loadup/modules/audit/infrastructure/autoconfigure/AuditPersistenceAutoConfiguration.java`
-
`loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/src/main/java/io/github/loadup/modules/audit/infrastructure/dataobject/AuditEventDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/src/main/java/io/github/loadup/modules/audit/infrastructure/repository/AuditGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-app/src/main/java/io/github/loadup/modules/contract/app/autoconfigure/ContractAutoConfiguration.java`
- `loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/dataobject/CatalogVersionDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/dataobject/ContractRevisionDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/dataobject/MerchantContractDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/repository/CatalogGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/repository/MerchantContractGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-test/src/test/resources/application-ci.yml`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-test/src/test/resources/application-test.yml`
- `loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/java/io/github/loadup/modules/dictionary/infrastructure/autoconfigure/DictionaryPersistenceAutoConfiguration.java`
-
`loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/java/io/github/loadup/modules/dictionary/infrastructure/dataobject/DictionaryItemDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/java/io/github/loadup/modules/dictionary/infrastructure/dataobject/DictionaryTypeDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/java/io/github/loadup/modules/dictionary/infrastructure/repository/DictionaryGatewayImpl.java`
- `loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/java/io/github/loadup/modules/file/infrastructure/autoconfigure/FilePersistenceAutoConfiguration.java`
-
`loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/java/io/github/loadup/modules/file/infrastructure/dataobject/FileReferenceDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/java/io/github/loadup/modules/file/infrastructure/dataobject/FileResourceDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/java/io/github/loadup/modules/file/infrastructure/repository/FileResourceGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-app/src/main/java/io/github/loadup/modules/merchant/app/autoconfigure/MerchantAutoConfiguration.java`
- `loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-infrastructure/src/main/java/io/github/loadup/modules/merchant/infrastructure/dataobject/MerchantDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-infrastructure/src/main/java/io/github/loadup/modules/merchant/infrastructure/repository/MerchantGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-test/src/test/resources/application-ci.yml`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-test/src/test/resources/application-test.yml`
- `loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/java/io/github/loadup/modules/notification/infrastructure/autoconfigure/NotificationPersistenceAutoConfiguration.java`
-
`loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/java/io/github/loadup/modules/notification/infrastructure/dataobject/InboxMessageDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/java/io/github/loadup/modules/notification/infrastructure/repository/InboxGatewayImpl.java`
- `loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/java/io/github/loadup/modules/transfer/infrastructure/autoconfigure/TransferPersistenceAutoConfiguration.java`
-
`loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/java/io/github/loadup/modules/transfer/infrastructure/dataobject/TransferTaskDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/java/io/github/loadup/modules/transfer/infrastructure/dataobject/TransferTaskOptionDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/java/io/github/loadup/modules/transfer/infrastructure/repository/TransferGatewayImpl.java`
- `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/pom.xml`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/DepartmentDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/LoginLogDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/PermissionDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/RoleDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/RoleDepartmentDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/RolePermissionDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/UserDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/UserOAuthBindingDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/dataobject/UserRoleDO.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/DepartmentGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/LoginLogGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/PermissionGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/RoleGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/UserGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/UserOAuthBindingGatewayImpl.java`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
-
`loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/java/io/github/loadup/modules/upms/MyBatisTestConfig.java`
- `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/resources/application-ci.yml`
- `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-test/src/test/resources/application-test.yml`
- `loadup-framework/pom.xml`
- `loadup-pay/acquire/acquire-infrastructure/pom.xml`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/AcquireOrderDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/AcquireRecoveryRetryReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/AllocationRecoveryCursorDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/CheckoutSessionDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/CheckoutSessionRevocationReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/FeeAdjustmentDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/FeeRefundReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/MerchantNotificationDeliveryDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/MerchantNotificationWakeReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderCloseIntentDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderContractSnapshotDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderCreateIntentDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderExtensionReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderFeeDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderPaymentAllocationDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/OrderPaymentOutcomeDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/PaymentEventReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/dataobject/RefundNotificationReceiptDO.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/AcquireRecoveryRetryGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/AllocationRecoveryGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/CheckoutSessionGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/CheckoutSessionRevocationGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/FeeRefundReceiptGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/MerchantNotificationGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/MerchantNotificationWakeGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderClosureGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderCreationGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderExtensionGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderFeeGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderSnapshotReaderImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentEventReceiptGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentInitiationGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentOutcomeGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/RefundNotificationReceiptGatewayImpl.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/test/java/io/github/loadup/pay/acquire/infrastructure/repository/AcquiringFinancialPersistenceIT.java`
-
`loadup-pay/acquire/acquire-infrastructure/src/test/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderFeeGatewayImplTest.java`
- `loadup-pay/channel/channel-core/src/test/java/io/github/loadup/pay/channel/core/PayModuleBoundaryTest.java`
- `loadup-pay/customer/customer-infrastructure/pom.xml`
-
`loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/dataobject/MerchantActionGrantDO.java`
-
`loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/dataobject/MerchantRelationshipDO.java`
-
`loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/repository/MerchantAuthorizationGatewayImpl.java`
-
`loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/repository/MerchantFactsGatewayImpl.java`
- `loadup-pay/limitcenter/limitcenter-infrastructure/pom.xml`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitBucketDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitExecutionGrantDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitOperationGateDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitPolicyHeadDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitPolicyRevisionDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitRefundReceiptDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitReservationDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitReservationItemDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitRuleRuntimeDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/dataobject/LimitSettlementReceiptDO.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitPolicyGatewayImpl.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitRefundGatewayImpl.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitSettlementGatewayImpl.java`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/MerchantLimitGatewayImpl.java`
- `loadup-pay/payment/payment-app/src/test/java/io/github/loadup/pay/payment/app/service/RoutingProfileServiceTest.java`
- `loadup-pay/payment/payment-infrastructure/pom.xml`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/ChannelBindingChangeReceiptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/ChannelBindingRevisionDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/ChannelMerchantBindingDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/ChannelTransactionClaimDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/OrderRefundSummaryDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentActionDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentAttemptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentCaptureDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentCaptureLineDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentChannelReceiptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentCloseEvidenceDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentControlReceiptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentGateDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentInputDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentOrderDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentParentGateDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentRecoveryCursorDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentRecoveryRetryReceiptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentRefundCapacityDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/PaymentResultAnomalyDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundAcceptanceDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundAllocationPlanDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundChannelReceiptDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundOrderDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundRecoveryCursorDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundRequestDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundResultAnomalyDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RefundTransactionClaimDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/dataobject/RoutingProfileDO.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/ChannelBindingGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentAcceptanceGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentActionGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentAttemptGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCaptureGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCaptureReaderImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCloseEvidenceGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentControlGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentInputGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryRetryGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentResultGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentVerificationTargetReaderImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RecoveryInventoryReaderImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundAcceptanceGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundCallbackTargetGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundRecoveryGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundResultGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundSnapshotReaderImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundSummaryGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RoutingProfileGatewayImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/UnknownRecoveryReaderImpl.java`
-
`loadup-pay/payment/payment-infrastructure/src/test/java/io/github/loadup/pay/payment/infrastructure/repository/FinancialPersistenceIT.java`
- `loadup-pay/product/product-infrastructure/pom.xml`
-
`loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/dataobject/ContractLimitPublicationDO.java`
-
`loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/dataobject/OrderAdmissionDO.java`
-
`loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/CurrentContractGatewayImpl.java`
-
`loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/OrderAdmissionGatewayImpl.java`
-
`loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/PolicyPublicationGatewayImpl.java`
- `loadup-pay/realtime/realtime-infrastructure/pom.xml`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/dataobject/ProjectionReceiptDO.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/dataobject/TradeOrderDO.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/dataobject/TradePaymentDO.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/dataobject/TradeRefundDO.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/dataobject/TradeSummaryDO.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/repository/ProjectionReceiptGatewayImpl.java`
-
`loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/repository/TradeProjectionGatewayImpl.java`
- `loadup-pay/reconciliation/reconciliation-infrastructure/pom.xml`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationBatchDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationDifferenceDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationEntryDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationImportDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationInputDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationReviewDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationReviewEventDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationReviewProposalDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationStatementDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/dataobject/ReconciliationVerificationDO.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationBatchGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationImportGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationInputGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationResultGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationReviewGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationStatementGatewayImpl.java`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationVerificationGatewayImpl.java`

## 待审查持久化入口候选（81）

文件名搜索仅提供入口候选，可能遗漏不同命名或非 Java 实现；各项行为尚未逐一核实。

- [ ] 
  `loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/java/io/github/loadup/components/delayedtask/DelayedTaskStore.java`
- [ ] 
  `loadup-framework/loadup-components/security/loadup-components-signature/src/main/java/io/github/loadup/components/signature/request/RedisRequestNonceStore.java`
- [ ] 
  `loadup-framework/loadup-components/security/loadup-components-signature/src/main/java/io/github/loadup/components/signature/request/RequestNonceStore.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/src/main/java/io/github/loadup/modules/audit/infrastructure/repository/AuditGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/repository/CatalogGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/java/io/github/loadup/modules/contract/infrastructure/repository/MerchantContractGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/java/io/github/loadup/modules/dictionary/infrastructure/repository/DictionaryGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/java/io/github/loadup/modules/file/infrastructure/repository/FileResourceGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-infrastructure/src/main/java/io/github/loadup/modules/merchant/infrastructure/repository/MerchantGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/java/io/github/loadup/modules/notification/infrastructure/repository/InboxGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/java/io/github/loadup/modules/transfer/infrastructure/repository/TransferGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/DepartmentGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/LoginLogGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/PermissionGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/RoleGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/UserGatewayImpl.java`
- [ ] 
  `loadup-framework/loadup-modules/loadup-modules-upms/loadup-modules-upms-infrastructure/src/main/java/io/github/loadup/modules/upms/infrastructure/repository/UserOAuthBindingGatewayImpl.java`
- [ ] `loadup-pay/acquire/acquire-client/src/main/java/io/github/loadup/pay/acquire/rpc/AcquireRpcMapper.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/AcquireRecoveryRetryGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/AllocationRecoveryGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/CheckoutSessionGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/CheckoutSessionRevocationGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/FeeRefundReceiptGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/MerchantNotificationGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/MerchantNotificationWakeGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderClosureGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderCreationGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderExtensionGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/OrderFeeGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentEventReceiptGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentInitiationGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/PaymentOutcomeGatewayImpl.java`
- [ ] 
  `loadup-pay/acquire/acquire-infrastructure/src/main/java/io/github/loadup/pay/acquire/infrastructure/repository/RefundNotificationReceiptGatewayImpl.java`
- [ ] `loadup-pay/customer/customer-client/src/main/java/io/github/loadup/pay/customer/rpc/CustomerRpcMapper.java`
- [ ] 
  `loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/repository/MerchantAuthorizationGatewayImpl.java`
- [ ] 
  `loadup-pay/customer/customer-infrastructure/src/main/java/io/github/loadup/pay/customer/infrastructure/repository/MerchantFactsGatewayImpl.java`
- [ ] 
  `loadup-pay/limitcenter/limitcenter-client/src/main/java/io/github/loadup/pay/limitcenter/rpc/LimitCenterRpcMapper.java`
- [ ] 
  `loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitPolicyGatewayImpl.java`
- [ ] 
  `loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitRefundGatewayImpl.java`
- [ ] 
  `loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/LimitSettlementGatewayImpl.java`
- [ ] 
  `loadup-pay/limitcenter/limitcenter-infrastructure/src/main/java/io/github/loadup/pay/limitcenter/infrastructure/repository/MerchantLimitGatewayImpl.java`
- [ ] `loadup-pay/payment/payment-client/src/main/java/io/github/loadup/pay/payment/rpc/PaymentCaptureRpcMapper.java`
- [ ] `loadup-pay/payment/payment-client/src/main/java/io/github/loadup/pay/payment/rpc/PaymentRpcMapper.java`
- [ ] `loadup-pay/payment/payment-client/src/main/java/io/github/loadup/pay/payment/rpc/PaymentStatementRpcMapper.java`
- [ ] 
  `loadup-pay/payment/payment-client/src/main/java/io/github/loadup/pay/payment/rpc/PaymentVerificationRpcMapper.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/channel/PaymentChannelGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/channel/PaymentChannelNotificationGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/ChannelBindingGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentAcceptanceGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentActionGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentAttemptGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCaptureGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentCloseEvidenceGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentControlGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentInputGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentRecoveryRetryGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/PaymentResultGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundAcceptanceGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundCallbackTargetGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundRecoveryGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundResultGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RefundSummaryGatewayImpl.java`
- [ ] 
  `loadup-pay/payment/payment-infrastructure/src/main/java/io/github/loadup/pay/payment/infrastructure/repository/RoutingProfileGatewayImpl.java`
- [ ] `loadup-pay/product/product-client/src/main/java/io/github/loadup/pay/product/rpc/ProductRpcMapper.java`
- [ ] 
  `loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/CurrentContractGatewayImpl.java`
- [ ] 
  `loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/OrderAdmissionGatewayImpl.java`
- [ ] 
  `loadup-pay/product/product-infrastructure/src/main/java/io/github/loadup/pay/product/infrastructure/repository/PolicyPublicationGatewayImpl.java`
- [ ] `loadup-pay/realtime/realtime-client/src/main/java/io/github/loadup/pay/realtime/rpc/RealtimeRpcMapper.java`
- [ ] 
  `loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/repository/ProjectionReceiptGatewayImpl.java`
- [ ] 
  `loadup-pay/realtime/realtime-infrastructure/src/main/java/io/github/loadup/pay/realtime/infrastructure/repository/TradeProjectionGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-client/src/main/java/io/github/loadup/pay/reconciliation/rpc/ReconciliationRpcMapper.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationBatchGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationImportGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationInputGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationResultGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationReviewGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationStatementGatewayImpl.java`
- [ ] 
  `loadup-pay/reconciliation/reconciliation-infrastructure/src/main/java/io/github/loadup/pay/reconciliation/infrastructure/repository/ReconciliationVerificationGatewayImpl.java`

## 主资源 Flyway 脚本（22）

-
`loadup-framework/loadup-components/data/loadup-components-database/src/main/resources/db/migration/V2026101201__sequence_allocation.sql`
-
`loadup-framework/loadup-components/data/loadup-components-dfs/loadup-components-dfs-binder-database/src/main/resources/db/migration/V1__create_file_storage.sql`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/resources/db/migration/V1__gotone_store.sql`
-
`loadup-framework/loadup-components/integration/loadup-components-gotone/loadup-components-gotone-store-jdbc/src/main/resources/db/migration/V2__add_notification_record_trace_id.sql`
-
`loadup-framework/loadup-components/reliability/loadup-components-delayedtask/src/main/resources/db/migration/V20261019000001__create_delayed_task.sql`
-
`loadup-framework/loadup-components/reliability/loadup-components-globalunique/src/main/resources/db/migration/V20260225000001__create_global_unique_postgresql.sql`
-
`loadup-framework/loadup-components/reliability/loadup-components-outbox/src/main/resources/db/migration/V20261008000001__create_outbox.sql`
-
`loadup-framework/loadup-modules/loadup-modules-audit/loadup-modules-audit-infrastructure/src/main/resources/db/migration/V20261002000001__create_audit_event.sql`
-
`loadup-framework/loadup-modules/loadup-modules-contract/loadup-modules-contract-infrastructure/src/main/resources/db/migration/V20261009000001__create_contract_catalog.sql`
-
`loadup-framework/loadup-modules/loadup-modules-dictionary/loadup-modules-dictionary-infrastructure/src/main/resources/db/migration/V20261002000002__create_data_dictionary.sql`
-
`loadup-framework/loadup-modules/loadup-modules-file/loadup-modules-file-infrastructure/src/main/resources/db/migration/V20261002000003__create_file_resource.sql`
-
`loadup-framework/loadup-modules/loadup-modules-merchant/loadup-modules-merchant-infrastructure/src/main/resources/db/migration/V20261010000001__create_merchant_profile.sql`
-
`loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/resources/db/migration/V20261003000001__create_notification_inbox.sql`
-
`loadup-framework/loadup-modules/loadup-modules-notification/loadup-modules-notification-infrastructure/src/main/resources/db/migration/V202610100102__inbox_common_persistence_fields.sql`
-
`loadup-framework/loadup-modules/loadup-modules-transfer/loadup-modules-transfer-infrastructure/src/main/resources/db/migration/V20261003000002__create_transfer_task.sql`
-
`loadup-pay/acquire/acquire-infrastructure/src/main/resources/db/migration/V20261021210000__acquire_order_creation.sql`
-
`loadup-pay/customer/customer-infrastructure/src/main/resources/db/migration/V20261021160000__create_customer_authorization.sql`
-
`loadup-pay/limitcenter/limitcenter-infrastructure/src/main/resources/db/migration/V20261021180000__limitcenter_policy_and_reservation.sql`
- `loadup-pay/payment/payment-infrastructure/src/main/resources/db/migration/V20261022020000__payment_acceptance.sql`
-
`loadup-pay/product/product-infrastructure/src/main/resources/db/migration/V20261021170000__create_product_admission.sql`
- `loadup-pay/realtime/realtime-infrastructure/src/main/resources/db/migration/V20261023020000__trade_projections.sql`
-
`loadup-pay/reconciliation/reconciliation-infrastructure/src/main/resources/db/migration/V20261027000000__reconciliation_core.sql`

## 未完成与证据边界

- 修复 reconciliation 对账单及导入 SQL 中的 SHA-256 字段映射；请求键竞争使用明确冲突目标和专用重试异常。审核请求键和主记录
  ID 竞争使用明确冲突目标；其他唯一约束错误继续透传。

- reconciliation 审核主记录、提案和事件读写已切换 JDBC。

- reconciliation 差异记录读写已切换 JDBC，批量插入显式生成 ID，差异标志与软删除改为布尔。

- reconciliation 输入清单与明细读写已切换 JDBC，明细插入分批执行。

- reconciliation 批次网关与结果网关的批次读写已切换 JDBC，软删除改为布尔。

- reconciliation 导入请求读写已切换 JDBC，软删除改为布尔；相关持久化验证尚未运行。

- reconciliation 对账单请求读写已切换 JDBC，软删除改为布尔；尚需数据库验证。

- reconciliation 验证请求读写已切换 JDBC，软删除改为布尔类型；尚需数据库验证。

- acquire 事务重试已收窄到显式业务键竞争和暂态异常；额外唯一约束错误不再被当成幂等竞争。

- acquire 商户通知投递写入已切换 JDBC，acquire 源码的 Flex Mapper 引用已清理；仍需完整构建和数据库验证。

- acquire 商户通知投递查询、分页和锁定读取已切换 JDBC；插入与更新仍使用 Flex。

- acquire 费用主记录和调整记录已切换 JDBC；相关测试桩已更新，尚未运行。

- acquire 费用调整记录已切换 JDBC，费用主记录更新仍使用 Flex；测试桩已同步修改，尚未运行。

- acquire 订单关闭网关所有入口均显式校验当前租户；费用调整记录的迁移仍待完成。

- acquire 订单创建流程中的初始费用插入已切换 JDBC；费用锁定和后续调整仍使用 Flex。

- acquire 订单创建意图读写已切换 JDBC；订单费用等表仍待迁移。

- acquire 订单合同快照已切换 JDBC；创建意图和订单费用仍待迁移。

- acquire 订单主表读写已切换 JDBC，软删除改为布尔；创建意图、合同快照与费用等相关表仍需迁移。

- acquire 订单快照锁定读取已切换 JDBC；订单主表其余创建与读取入口仍待迁移。

- acquire 支付发起及结果处理的订单条件更新已切换 JDBC；订单创建和查询等入口仍待迁移。

- acquire 关闭流程的订单状态条件更新已切换 JDBC；订单主表其他入口仍使用 Flex。

- acquire 付款分配的创建、查询与结果更新已切换 JDBC，表软删除已改为布尔；关联订单仍待迁移。

- acquire 付款分配的操作键读取和输入绑定更新已切换 JDBC；分配创建及其他入口仍需迁移。

- acquire 关闭流程中的付款分配扫描与结清核对已切换 JDBC；付款分配的其他入口仍使用 Flex，软删除暂为数值类型。

- acquire 付款结果回执已切换 JDBC，关闭流程核对结果数也使用 JDBC；关联订单与付款分配仍需迁移。

- acquire 订单关闭意图的读取、插入与游标更新已切换 JDBC；关闭流程中的订单与付款分配仍依赖 Flex。

- acquire 收银台会话已切换 JDBC；初始化脚本的时间精度和 JSON 检查已改为 PostgreSQL 表达式。跨组件集成测试中已移除被淘汰的延迟任务
  Mapper 引用，尚未运行验证。

- acquire 订单延期回执和分配恢复游标已切换 JDBC；订单延期对主表的条件更新仍使用主表现有的数值软删除字段，待主表全部入口迁移时统一修改。

- `acquire` 的支付事件、退款通知、退款手续费、恢复重试、通知唤醒和会话撤销回执已切换 JDBC；其余订单、会话及通知等表仍需迁移，整个
  acquire 事务重试范围仍需收窄。

- `payment` 源码的 Flex 和 MyBatis 引用已清理；本地事务仅重试特定业务键冲突及暂态数据访问异常。该结论来自静态搜索，尚未验证构建、约束和并发行为。

- `payment` 渠道绑定主记录、修订和变更回执已改为 JDBC，软删除与能力标志使用布尔类型；尚未执行数据库验证。

- `payment.refund_order` 的创建、结果更新、快照、受理证据、回调定位和对账采集入口已改为 JDBC；`deleted` 已改为布尔类型，仍需数据库与并发验证。

- 逐入口行为清单、跨模块引用、约束与索引盘点尚未完成；不能据此认为方案批次 0 已完成。
- 外部 SDK 消费者及 Maven 实际依赖图未知；本工作空间搜索不证明外部无引用。
- 未运行构建、测试或数据库验证；回滚行为及并发安全需要后续环境验证。
