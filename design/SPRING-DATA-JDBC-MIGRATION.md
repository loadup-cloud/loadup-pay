# 移除 MyBatis-Flex 并迁移到 Spring Data JDBC

状态：实施中。本文件描述迁移目标和工作顺序，不表示迁移已经完成或经过运行验证；当前静态进展见 [迁移盘点](SPRING-DATA-JDBC-INVENTORY.md)。

## 目标和范围

在 `loadup-pay` 及其依赖的 `loadup-framework` 中完全移除 MyBatis-Flex，并以 Spring Data JDBC 和 Spring JDBC
实现持久化。框架作为独立发布的 SDK，其自身仍使用 Flex 的模块也要纳入迁移，不能只清理 Pay
的直接依赖；实施时先盘点两个仓库的生产代码、测试源码、构建插件、BOM、配置及生成代码，明确其他消费工程的引用并报告影响。允许破坏性修改，不提供旧
API 兼容层，也不进行历史数据迁移。

数据库目标为 PostgreSQL。Pay 每个业务模块保留一份完整的 Flyway 初始脚本；共享组件分别拥有自己的初始脚本。框架中仍保留 MySQL
脚本或多个迁移文件的模块，实施时应按其实际使用范围单独处理，不能宣称整个 Framework 的 PostgreSQL
改造已经完成。迁移不改变现有模块边界：数据库访问位于 infrastructure，跨模块调用从 infrastructure 访问对方 client；不在
client 和 domain 重复定义枚举。

目标模型不建立数据库外键约束，也不依赖数据库级级联。关系完整性由下面的应用契约、数据库唯一约束和检查约束共同维护；移除外键不能同时移除业务校验。

## 技术选择

- **Spring Data JDBC**：优先负责简单、聚合边界明确的持久化，以及适合派生查询的简单读取。持久化实体留在基础设施层，不把 Spring
  Data 注解引入 domain 或 client。
- **Spring JDBC**：使用 `JdbcClient` 或 `NamedParameterJdbcTemplate` 执行参数化
  SQL，负责支付、对账、可靠性组件等需要明确控制锁、条件更新、批量操作、PostgreSQL 特性和返回行数的路径。针对结果映射复杂度选择明确的
  `RowMapper` 或其他 Spring JDBC 映射方式。
- 两者使用同一个 Spring 管理的 `DataSource` 和本地 JDBC 事务管理器。默认不引入 MyBatis、MyBatis Dynamic SQL 或另一套
  ORM；不是所有表都必须建立 Spring Data Repository。

已有数据对象和领域聚合之间通过现有 Gateway/Repository 边界和转换代码连接。Spring Data JDBC
的聚合是持久化映射概念，不应直接假设与领域聚合完全一致。跨聚合、跨模块和报表读取使用明确的查询与投影，不通过级联映射把整个业务模型装入一次
`save`。

## 实现规则

### 读写、锁和并发

1. 单行且无关联子实体的常规 CRUD，可使用 Spring Data JDBC Repository。是否使用 `save` 需明确新增/更新判定，尤其检查应用生成
   ID 与 `@Version` 的组合。
2. 资金状态流转、版本围栏、幂等、去重、领取与租约等写入，使用带业务键、租户、当前状态或版本条件的显式
   SQL，检查受影响行数。零行更新应按调用场景明确映射为冲突、无记录或幂等结果，不能统一吞掉。
3. 仅在确有需要的事务中使用 `FOR UPDATE` 等锁；扫描、领取是否使用 `SKIP LOCKED` 由现有并发契约决定。PostgreSQL
   `ON CONFLICT` 必须指定正确的冲突目标，不能将其他唯一约束错误误判为业务幂等。
4. Spring Data JDBC 对已存在的含关联子实体的聚合执行 `save` 时，会删除并重新插入关联实体；迁移前逐一评估外键、审计及写入量。复杂聚合更新使用显式
   SQL。`@Lock` 可用于派生查询；字符串形式的 `@Query` 不应依赖 `@Lock`，需要在 SQL 中写明锁语义。
5. 动态筛选使用固定 SQL 片段与绑定参数组合，排序字段从白名单选择；不得拼接用户输入。分页、排序、空值及类型转换须保留原业务语义。优先按场景编写查询，不另造通用
   ORM 或查询 DSL。

### 无外键时的关系治理

- **同库聚合内部**
  ：创建子记录前校验父记录的存在、租户归属和允许关联的状态；涉及并发变更时，对父记录使用锁或状态条件更新，在同一事务内完成关联写入。聚合根删除、作废或清理时由应用服务显式编排子记录处理和根记录变更，禁止依赖数据库级联。需要保留的金融流水和审计证据应保留或归档，不得为了清除引用而物理删除；处理顺序以业务不变量为准。
- **跨聚合和跨服务**
  ：仅保存对方标识和必要的业务快照，不做实体级联或跨库事务。写入前通过领域服务或已验证、具有明确有效期及失效策略的权威缓存检查标识、租户和状态；不可验证时按业务策略拒绝或延后处理，不默认放行。服务间核验须在本地事务外完成，并考虑核验到提交间状态变化：重要业务需要不可变快照、版本/有效期、对方业务承诺或后续对账及补偿机制，单次
  RPC 或缓存校验不能提供跨服务原子性。
- 为可清理的数据定义删除权限、引用检查、归档和恢复策略，并提供按业务键及租户识别悬挂引用的核查与处置方式；已有不可变流水按保留政策处理。索引和唯一约束仍须覆盖关联及幂等查询。

### 冲突重试与任务领取

- `@Version` 更新冲突可产生 `OptimisticLockingFailureException`；显式 SQL 的版本条件更新则需通过零行结果识别冲突。冲突本身通常是更新零行后由应用抛出的异常，不应一概视为
  PostgreSQL SQL 错误或事务已中止。约束错误等数据库异常则可能使事务无法继续，必须回滚事务或回滚到有效保存点。
- 为可安全重试的高并发操作制定有限次数、退避与随机抖动策略；每次重试在独立的新事务中重新读取状态、重新执行业务判断和完整本地写入。重试切面或
  `RetryTemplate`
  应位于事务边界之外，避免同一个失败事务内重试。重试前确保操作幂等，外部调用不在本地事务重试中重复执行；金额不足、非法状态等业务拒绝直接返回，耗尽后返回明确冲突或交由已有任务机制处理，不对所有操作无条件重试。
- DelayedTask 与 Outbox 的多实例扫描统一采用基于 PostgreSQL `FOR UPDATE SKIP LOCKED` 的非阻塞领取设计；领取和状态、lease、token、attempt
  的更新须在同一短事务内完成并提交后才执行处理。后续确认、重试及过期回收必须保留 token/generation 围栏。示意
  SQL（实际表名和字段以迁移后的 schema 为准）：

  ```sql
  SELECT id
    FROM outbox
   WHERE status = 'PENDING' AND next_retry_at <= CURRENT_TIMESTAMP
   ORDER BY next_retry_at, id
   LIMIT :batch_size
   FOR UPDATE SKIP LOCKED;
  ```

  锁只在事务内有效，单独执行这个查询并提交并不构成持久领取。`SKIP LOCKED` 适用于队列争抢，不适用于要求完整视图的业务查询；须处理可能长期跳过的记录和租约过期恢复。
- 涉及多行、多账户和多表锁时，定义所有参与路径一致的表级顺序及同表主键升序，并在代码审查中核对。统一顺序降低死锁概率，不能保证数据库内部锁、不同访问路径或外部资源绝不发生死锁；可重试的死锁和序列化失败需在完整事务边界外按上述规则处理。

### 事务和错误处理

业务数据、Outbox、DelayedTask 及同库可靠性记录必须使用相同数据源和事务管理器；在既有事务中的 Spring JDBC 调用要使用 Spring
管理的连接。跨服务调用不纳入本地数据库事务。

PostgreSQL 中 SQL 错误可能使当前事务进入失败状态。迁移 GlobalUnique、DelayedTask 和 Inbox 时，优先使用语义明确的
`ON CONFLICT`；其他必须处理的约束冲突需要正确使用事务保存点或调整事务边界，不能捕获异常后直接假定事务仍可继续。保存点操作失败必须使外层事务失败。保持现有幂等键、重试、lease
和 fencing 语义。

当前 `JdbcTransactionAutoConfiguration` 包含与 Flex/MyBatis 会话工厂衔接的代码，且最近修改尚未运行验证。全部移除 Flex
后，应删除仅用于该衔接的配置和依赖；保留或调整 Spring JDBC 事务管理及保存点能力时，以最终装配为准，不能保留无用的 MyBatis
事务工厂或 BeanPostProcessor。

### 公共字段、租户和转换

- 盘点并替换 `BaseDO`、`BaseEntityListener`、Flex 注解及自动填充承担的 ID、创建时间、更新时间、软删除和租户能力。ID
  生成使用现有约定或明确的新实现；时间使用项目已有 `Clock` 和时区契约。
- 为 Repository 保存路径配置必要的 Spring Data auditing、回调和转换；对于直接执行的 SQL，在语句中明确维护相应字段。
  `@Modifying` 查询不会触发实体回调或自动更新审计字段。
- 所有需要租户隔离的读取、修改和删除都应显式包含可信租户条件；新增写入明确设置租户字段。避免因绕过 Repository、批量操作或原生
  SQL 导致隔离失效。
- 逐项规定枚举、JSON、金额、时间、可空字段和 PostgreSQL 类型的映射。领域枚举仍由 domain 拥有；不为持久化重复创建同义枚举。数据库约束和唯一键以实际业务语义为准。

### PostgreSQL 类型和 ID

- **JSON**：现有证据字段按 PostgreSQL 基线保留 `TEXT`，不因迁移 ORM 自动转成 `JSONB`；确有结构化查询或索引需求时，设计对应
  `JSONB` 列和索引。Spring Data JDBC 使用 `JsonNode` 或自定义对象映射 `JSONB` 时，注册明确的 `@ReadingConverter` /
  `@WritingConverter` 和 `JdbcCustomConversions`，必要时通过 `PGobject` 指定 `jsonb` 类型；显式 SQL
  同样明确参数类型。保留原始报文的字节、签名或格式语义时，单独保存原文，不能依赖 JSONB 维持原始格式。
- **ID**：保留各模块现有 ID 类型、全局唯一性与业务标识语义，先检查现有 sequence 分配器；不能将所有主键强制改为数据库自增。采用数据库生成的数值
  ID 时，选择 PostgreSQL `IDENTITY` 或 sequence，并实现 Spring Data JDBC `@Id` 及插入后 ID 回填；应用预分配 sequence 或已有
  ID 生成器时，通过 `BeforeConvertCallback` 或显式插入赋值，配合正确的新实体判定。两种生成方式不得对同一列混用而产生冲突。
- **布尔和枚举**：布尔语义的新表列使用 PostgreSQL `BOOLEAN`，Java 使用 `boolean` / `Boolean` 并明确空值规则；检查软删除条件和所有
  SQL 参数，统一修改初始建表脚本。枚举持久化为约定稳定的字符串代码，默认使用枚举名称；存在既定业务代码的枚举保留其代码，通过显式转换器完成双向映射，不存储
  ordinal，不复制 domain 枚举。不得假定 `@Value` 是 Spring Data JDBC 的通用枚举持久化注解。

### 异常转换和批处理

- `JdbcTemplate`、`NamedParameterJdbcTemplate` 及其上的 `JdbcClient` 使用 Spring 的 `SQLExceptionTranslator` 将 JDBC
  `SQLException` 转为 `DataAccessException`；`JdbcTransactionManager` 也处理提交及回滚阶段的异常转换。根据 Spring Boot
  实际装配检查异常转换链；仅在自定义 Repository 等需要代理转换的场景确认 `PersistenceExceptionTranslationPostProcessor`
  是否已配置，不将它作为 JDBC 模板异常转换的前提。
- 幂等处理优先使用指定冲突目标的 `ON CONFLICT`，或根据实际异常类型、SQLSTATE 和约束名精确识别预期唯一冲突；不得把所有
  `DataIntegrityViolationException` 都当作重复请求。检查约束、非空约束等异常应按错误契约处理。
- 对账流水、批量记账等确定为批量高吞吐的路径使用 `NamedParameterJdbcTemplate.batchUpdate` 或等效 Spring JDBC 批量
  API，禁止逐条调用 Repository `save()` 充当批处理。按参数量、内存和事务时长设置批次边界；检查批处理结果、失败回滚和业务幂等。
- 对适合合并的 PostgreSQL 批量 INSERT，可配置 JDBC `reWriteBatchedInserts=true`；该选项不保证所有语句都被改写，也不保证固定倍数的收益。UPDATE
  和依赖逐行 `RETURNING` / 生成键的操作应单独确认驱动行为及结果语义；实际性能需由后续运行环境评估，当前不声称已验证。

## 优先级与实施批次

优先级表示实施顺序和风险，不表示后面的批次可以永久省略。 **P0—P3 均为完全移除 Flex 的必需工作**；P0 优先保护数据一致性并建立后续迁移的基础，P1
完成共享能力，P2 完成 Pay 业务，P3 完成其余框架模块和全仓收尾。每一批应记录改动清单、静态检查结论和未验证事项。

| 优先级 | 批次                      | 主要交付                                                                    | 前置条件                                 |
|--------|---------------------------|-----------------------------------------------------------------------------|------------------------------------------|
| P0     | 0. 盘点与边界             | 模块清单、依赖与生成代码清单、关键行为清单                                  | 无                                       |
| P0     | 1. 数据库基础设施         | Spring JDBC 装配、ID/租户/审计/事务替代方案                                 | 批次 0                                   |
| P0     | 2. 可靠性组件             | sequence、GlobalUnique、Outbox、DelayedTask 的持久化替换                    | 批次 1                                   |
| P1     | 3. Pay 使用的框架业务模块 | merchant、contract、file、transfer 等实际依赖模块                           | 批次 1；使用可靠性组件的路径还依赖批次 2 |
| P2     | 4. Pay 业务模块           | 七个业务服务和相关共享模块的持久化替换                                      | 批次 1—3 中对应的依赖                    |
| P3     | 5. 其余框架模块           | UPMS、audit、dictionary、notification、DFS、Gotone 等实际含 Flex 引用的模块 | 批次 1；按实际组件依赖排列               |
| P3     | 6. 删除依赖与交付         | 删除 Flex/MyBatis 及生成器、静态残留盘点、更新文档                          | 批次 0—5                                 |

表中模块是当前已知的起点，最终清单和模块顺序由批次 0 的依赖盘点确定。批次 4 与批次 5 在依赖满足后可以分开实施，但全部完成前不能宣称
Framework 已完全移除 Flex。

### P0 / 批次 0：盘点与设计冻结

1. 分别在 Pay、Framework 以及工作空间中实际消费它们的工程中搜索 Flex/MyBatis
   的生产及测试引用、POM/BOM、自动配置、注解处理器和生成类型；按模块记录直接依赖、传递依赖和外部消费者。静态 POM
   只能提供依赖声明线索，最终依赖图以用户执行构建后提供的结果为准。
2. 对每个 Gateway/Mapper 建立迁移清单：表、读写入口、事务边界、租户条件、锁、状态和版本条件、幂等键、异常、软删除、分页及排序。标记逐行保存的大批量路径、跨模块引用、外键和数据库级级联。
3. 确定 ID 与新实体判定、审计时间及软删除、枚举/JSON 转换、事务管理器、异常处理、重试和队列领取的具体方案，确认其与现有业务契约一致。盘点所有
   Flyway 文件和表结构，记录布尔列、约束及索引的修改项。
4. 选定以 Spring Boot 管理版本为基础的 Spring Data JDBC 与 JDBC 依赖；列出可删除的 Flex
   starter、processor、插件、配置及产物。识别受当前未验证的事务配置影响的模块。

**完成条件**：有可追踪到模块与入口的迁移清单、批次依赖及明确的持久化约定；未确定的跨服务一致性或外部消费者影响单列，不以推测代替。

### P0 / 批次 1：数据库基础设施

1. 在 Framework 数据库组件引入 Spring Data JDBC 和 Spring JDBC；配置单数据源的事务管理、Repository 扫描和必要的转换器。检查
   Spring Boot 自动配置与模块开关，避免注册多个互相冲突的事务管理器。
2. 拆除 `BaseDO`、`BaseEntityListener` 等对 Flex 的耦合，落地 ID 生成、插入/更新判定、`Clock`、审计和软删除策略。为显式 SQL
   明确列清单和公共字段赋值，不能假定 Repository 回调覆盖所有写入。
3. 实现可信租户上下文到查询条件的传递；为每种数据访问方式明确租户过滤和缺少租户时的行为，检查管理员及跨租户任务的授权边界。
4. 设计可复用但不过度封装的异常分类、事务外重试入口、批量操作和必要保存点能力；不在基础设施层偷偷重试不可幂等业务。
5. 将仅服务 Flex 的会话工厂和事务桥接标记为待删除，检查现有使用者。迁移期间可暂留实现以支持尚未迁移的源码；最终必须移除，不作为对外兼容方案。

**完成条件**：新基础能力及其调用契约已落地，所有保留 Flex 的过渡引用有明确归属；不能仅凭源码认定事务装配已运行成功。

### P0 / 批次 2：共享可靠性组件

1. **sequence**：替换 Flex 访问，保持序列区间分配、租户/业务键和并发唯一性语义；核对与 ID 生成器的调用关系。
2. **GlobalUnique**：改为明确冲突目标的 PostgreSQL SQL，保留首次登记、重复请求与非预期约束冲突的区分；审查调用方事务及保存点。
3. **Outbox 和 DelayedTask**：迁移登记、扫描、领取、确认、重试和过期恢复；使用短事务内的 `SKIP LOCKED` 领取并写入
   lease/token，保留围栏和幂等行为。核对索引、时间字段、批量大小和事务边界。
4. 以每个组件自身的一份 PostgreSQL 初始 Flyway 脚本承载最终结构；移除旧 SQL、Flex Mapper、表定义和已无用途配置。

**完成条件**：关键写入的条件、返回行数、租约和去重语义均能从源码与 SQL 追踪；真实数据库的并发与回滚结果仍标记为未验证。

### P1 / 批次 3：Pay 依赖的框架业务模块

1. 依批次 0 的实际依赖图迁移 merchant、contract、file、transfer 和其他被 Pay 引入的模块；逐个把查询映射、CRUD、状态更新及分页从
   Flex 转成 Repository 或显式 SQL。
2. 检查聚合根与子记录的创建、作废、归档，以及租户、软删除、跨模块弱引用校验。迁移不得把跨服务 RPC 放进本地数据库事务。
3. 合并模块 Flyway 初始脚本、替换 MySQL 类型和语法、删除外键并保留必要索引与约束；保留业务字段与关联语义。
4. 清理模块对 Flex 数据对象、生成 Mapper 和 TableDef 的引用，更新必要的消费者与模块说明。

**完成条件**：对应模块及其直接消费者在源码上不再使用 Flex；每个模块的数据访问及迁移脚本具有明确所有权。

### P2 / 批次 4：Pay 业务模块

建议顺序为 customer → product → limitcenter → realtime → acquire → payment → reconciliation；实际以模块依赖与批次 0
发现的风险为准。每个服务按同一流程处理：

1. 对照盘点清单替换 `@Table` 数据对象、Flex Mapper 和 `QueryWrapper`；简单单表读写使用 Spring Data JDBC，复杂查询及条件写入使用显式
   SQL，保留 Gateway 对 domain 的接口边界。
2. 逐一核对租户、业务键、空值、字段转换、更新行数、排序分页、软删除以及所有锁与版本条件；不通过 Repository 通用 `save`
   替代需要原子条件更新的路径。
3. 处理引用与清理：本地子记录按同一事务编排，跨服务只保留标识及所需快照，并明确校验、并发窗口和补偿职责。
4. 将布尔列及必要的类型调整直接合并进该服务唯一的 PostgreSQL 初始 Flyway 文件；按需要保留 `TEXT` 原始证据及金融审计记录。
5. 删除本服务的 Flex 依赖与生成类型引用，更新测试源码中失效的类型引用，但不执行测试用例。

重点审查：acquire/payment 的资金状态、幂等与 Outbox 同事务；realtime 的版本围栏；reconciliation
的输入封存、批量写入和结果原子提交。每个模块完成时列出已审查路径和未取得运行证据的路径。

### P3 / 批次 5：其余框架模块

1. 处理 Framework 中未被 Pay 直接使用但仍依赖 Flex 的 UPMS、audit、dictionary、notification、DFS 数据库 binder、Gotone JDBC
   store 等；实际范围以全仓盘点为准。
2. 按同样规则替换 Mapper、查询、事务、公共字段和数据类型。检查对外 SDK 公开类型和其他工作空间消费者；无法修改的外部消费者明确列出影响，不提供
   Flex 兼容实现。
3. 对涉及的数据库模块将 MySQL 脚本改为 PostgreSQL，并合并成各自一份初始 Flyway 脚本；数据库变更按新库建立处理，不编写历史数据迁移。

**完成条件**：Framework 的生产与测试源码不再依赖 Flex，相关模块的脚本和依赖声明完成静态清理；外部无法修改的消费者风险已记录。

### P3 / 批次 6：移除依赖与交付

1. 在所有消费者迁移后删除 Flex starter、MyBatis 依赖、注解处理器模块、相关 Maven
   配置、自动配置和生成类型；确认没有其他功能需要保留这些模块。同步清理测试源码引用，避免残留导致后续用户构建失败。
2. 全仓静态复查依赖声明、源码导入、配置、生成目录引用和 Flyway 脚本数量；审查 BOM、模块启停条件及共享基础设施的对外 API。
3. 更新 PostgreSQL 基线和事务文档，移除不再适用的 Flex 桥接描述；记录新增配置、破坏性变更、外部消费者影响和未执行的验证。
4. 如果需要编译、依赖树或运行环境证据，暂停并向用户提供最窄范围、带 `clean` 的手动构建命令；不执行任何测试用例，也不把静态检查结果表述为构建或运行通过。

**完成条件**：目标仓库源码和依赖声明无 Flex/MyBatis 残留，文档与实际设计一致；构建依赖图和运行状态仅在用户提供结果后报告，未取得的证据保持未验证。

## 完成条件与验证边界

- 静态盘点确认目标仓库不再引用 Flex API、starter、处理器、生成类型、插件及其配置；最终构建依赖图中不再包含 Flex 或不需要的
  MyBatis 依赖。
- 每个迁移模块的数据库读写都已审查：租户、ID、软删除、审计、类型转换、锁、更新行数、幂等和事务边界均有明确实现；Pay 每个业务模块只有一份
  PostgreSQL 初始迁移文件。
- 明确标记每项已完成的源码检查和尚未获得的构建、数据库运行证据。静态检查不能证明编译成功、Flyway 实际执行成功，或并发与回滚行为正确。
- 按当前用户要求， **不执行测试用例**。不自行启动构建；确需构建时暂停并通知用户手动操作，命令应带 `clean`
  并尽量限定范围。未获得用户提供的结果时不得声称构建或运行验证通过。

## 参考

- [Spring Data JDBC：实体持久化与乐观锁](https://docs.spring.io/spring-data/relational/reference/jdbc/entity-persistence.html)
- [Spring Data JDBC：查询与修改](https://docs.spring.io/spring-data/relational/reference/jdbc/query-methods.html)
- [Spring Data JDBC：事务与锁](https://docs.spring.io/spring-data/relational/reference/jdbc/transactions.html)
- [Spring Framework：JDBC 连接与事务](https://docs.spring.io/spring-framework/reference/data-access/jdbc/connections.html)
- [Spring Framework：JDBC 异常转换](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)
- [PostgreSQL：SELECT、行锁与 SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html)
- 当前 PostgreSQL 工作基线：[POSTGRESQL.md](POSTGRESQL.md)
