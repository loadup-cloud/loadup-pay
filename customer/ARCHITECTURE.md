# customer 架构

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 数据与事务

- `merchant_profile` 及其迁移归框架 merchant；`global_unique` 归 GlobalUnique；ID 和序列分配归 database。
- customer 仅新增 merchant_relationship、merchant_action_grant。实际迁移：
  `customer-infrastructure/src/main/resources/db/migration/V20261021160000__create_customer_authorization.sql`
  ；设计草案同步更新，不能重复执行草案。
- 创建、授权、撤销、资格判断均使用主库 READ_COMMITTED 事务。以收款商户主档行锁串行化该商户的授权变更和资格判断；撤销另校验原资源版本。数据库唯一索引作为最终约束。
- 撤销保留原ID和版本历史。PostgreSQL 部分唯一索引限制每个业务键最多存在一个 INIT 或 ACTIVE
  记录。撤销后可用新ID和新幂等键重新授予；到期记录仍需显式撤销。旧关系绑定的授权不会自动改绑到新关系。
- 有效窗口是左闭右开；授权与关系同时满足才可代操作。入参统一转为上海偏移，拒绝超过微秒精度的有效时间，避免 DATETIME (6)
  截断改变边界。
- requestKey 按租户、操作版本、主体类型和主体ID隔离。执行器保存参数 SHA-256 与首次响应快照；同键同参返回首次快照，同键异参报
  CUSTOMER_IDEMPOTENCY_CONFLICT。租户同时进入哈希键，回放再次核对租户精确一致，避免依赖表的大小写不敏感排序规则混淆租户。获取商户行锁后再次检查，处理并发重试。
- GlobalUnique claim 与业务写入使用同一数据源、同一事务；失败一起回滚。重复请求返回首次结果，即使资源后来被撤销，下一次资格判断仍读取最新状态。幂等记录不设置TTL，不通过软删除复用键。
- 关系与授权使用 MyBatis-Flex 读取和参数化 Spring JDBC 写入；租户由 schema 隔离，表内不保存租户列，显式维护时间、状态和版本。MapStruct
  使用框架共享配置。
