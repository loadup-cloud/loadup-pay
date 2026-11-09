# 当前应用边界

商户主档、经营状态、关系、逐项动作授权和安全商户摘要的权威服务。原 merchant 模块已合并到本应用；不再提供框架业务模块或浏览器管理入口。

商户档案、关系和授权使用同一个商户数据库。签约数据归 productcenter，本应用不复制或直接操作产品合约表。

交易查询与业务命令仅通过 `/api/internal/v1/` 提供。管理应用转发已验证的原操作者 JWT；中心再次校验权限，并在进入数据库事务前绑定签名中的 tenant_id。不信任请求体中的操作者身份或租户 header。

当前管理入口要求 OPERATOR 身份。令牌须同时包含当前后管及目标 center 的 audience；跨商户签约查询两域时应包含 merchantmng、productcenter、merchantcenter 所需受众。现有 AuthServer 仍仅提供 SERVICE 的 client_credentials；本次拆分没有新增操作员登录或商户 JWT 签发。

历史设计中的 customer/product 名称及框架模块归属以当前 README 和根架构的四应用划分为准；以下保留事务与业务规则说明。

# customer 架构

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 数据与事务

- `merchant_profile` 及其迁移归框架 merchant；`global_unique` 归 GlobalUnique；ID 和序列分配归 database。
- customer 仅新增 merchant_relationship、merchant_action_grant。实际迁移：
  `customer-infrastructure/src/main/resources/db/migration/pay/merchantcenter/V20261021160000__create_customer_authorization.sql`
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
