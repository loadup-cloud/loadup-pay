# PostgreSQL 开发基线与验收

状态：迁移源码已整理；真实 PostgreSQL 初始化、并发和故障恢复验收尚待执行。不要把 SQL 语法检查、编译或已有测试源码视为运行通过。

## 配置与所有权

acquire、payment、customer、product、limitcenter、realtime、reconciliation 各自使用独立数据库或隔离
schema；一个服务不得依赖另一个服务的表或事务。各服务 business profile 使用对应的 `<SERVICE>_JDBC_URL`、
`<SERVICE>_JDBC_USERNAME` 和 `<SERVICE>_JDBC_PASSWORD`，URL 应为 PostgreSQL JDBC URL。启动时 `classpath:db/migration`
同时加载该服务 infrastructure 和其实际依赖 JAR 的共享组件迁移。只在新建的可丢弃开发库上执行初次验证，不对已有库自动执行
Flyway clean、repair 或数据搬迁。

每个支付服务的 infrastructure 只有一份初始 Flyway 文件。sequence、Outbox、DelayedTask、GlobalUnique，以及该服务实际引入的
merchant、contract、File 和 Transfer 迁移由各自模块拥有。安装 JAR 时应先 clean：旧构建输出可能仍含已删除的 MySQL
或增量迁移，即使源码目录已经干净也会被 Flyway 扫描。

单数据源装配使用 Spring JDBC 事务管理器，让业务写入、Outbox 和本地 JDBC 使用同一数据源；Outbox 会拒绝不匹配的事务管理器。
GlobalUnique、DelayedTask 和 Inbox 的幂等插入使用指定业务唯一键的 `ON CONFLICT DO NOTHING`，不将其他约束错误当作重复请求。
事务中必须使用绑定到相同数据源的 Spring 管理连接；真实数据库并发、回滚及事务装配仍需验收。

时间字段使用 `timestamp(6) without time zone`，JVM、业务 Clock、JDBC 和数据库会话采用同一部署本地时区；显式偏移的协议时间仍表示绝对时间。字符串列使用
`C` collation；金额、版本和序列保持整数，证据 JSON 保持 TEXT。迁移不承诺与原 MySQL
的区域排序、尾空格和所有字符比较完全相同，必须在目标数据库上验证业务身份及分页语义。

## 必须留存的验收结果

记录 PostgreSQL 服务端和 JDBC/Flyway 版本、数据库或 schema、应用时区、迁移清单、执行命令及结果。现有 Testcontainers 默认镜像为
`postgres:15-alpine`；这是测试默认值，不是部署版本的决定。

1. 对所有服务及其实际依赖分别在新库初始化，确认无重复 Flyway 版本、无 MySQL 语法，表、索引和约束均存在。
2. 在同一事务内重复提交 GlobalUnique、Inbox 和 DelayedTask 的业务键，验证去重之后仍能继续写入；不相关唯一键冲突必须报错，外层回滚必须撤销全部本地写入。验证
   sequence 分配并发无重复。
3. 验证 Outbox 领取、重试与过期租约，以及 DelayedTask 的 claim token、attempt、旧 lease 和旧 generation 的围栏。
4. 验证支付幂等、首次提交资格、状态与 receipt/Outbox/task 的同事务回滚、退款上限、realtime 旧版本拒绝覆盖；验证金额、微秒、账单日界和
   RPC 时间往返。
5. 验证对账读取 RR、双侧输入封存、结果原子提交；使比较任务耗尽并显示 FAILED，修复故障后重试原 run/task，检查过时
   task、跨租户、运行中、已完成和并发重试均受控，最终只提交一次结果。
6. 记录常见查询计划，尤其运营分页和任务扫描；只有数据量与计划证明需要时再优化索引或分页。

测试使用 Testcontainers 隔离环境；支付持久化 IT 使用各 infrastructure 模块的 `postgresql-it` profile。构建和测试由开发者手动执行，统一使用带
`clean` 的窄模块命令，不能以单个测试结果推断七服务全部通过。
