# ADR 0001 · 单租户部署，SaaS 化后再启用 schema 路由

- 状态：已采纳
- 日期：2026-11-07

## 背景

`loadup.database.multi-tenant` 设计与代码已经支持 schema-per-tenant，但运行时只绑定默认租户；此前各服务配置为 `enabled=true`、`required=true`，形成“配置声称强制多租户、实际只服务默认 schema”的中间态。多租户目前没有真实需求：`loadup-pay` 部署一套即服务一个租户。

## 决策

1. 当前所有服务按**单租户**运行：`loadup.database.multi-tenant.enabled=false`、`required=false`，业务库固定使用配置的 `default-schema`。
2. `DatabaseTenant` 在单租户模式下不再把租户 ID 解释为物理 schema；若执行上下文携带非默认租户，直接抛 `TENANT_NOT_SUPPORTED`，不静默落到默认 schema。
3. 保留以下零成本钩子，供未来 SaaS 化启用：
   - JWT 继续携带逻辑 `tenant_id`（默认 `__default__`）；
   - 业务表不写 `tenant_id` 列；
   - Redis key、DFS 路径、任务身份继续带租户命名空间；
   - `ExecutionContext` / `TenantContext` 不删除。
4. 未来启用条件：SaaS 化明确立项后，再实现租户注册表、`tenantId → schema` 映射、事务前连接路由、分 schema Flyway 与后台任务绑定；届时把 `enabled` 打开并补 `required` 语义。

## 影响

- 消除“假多租户”安全风险：非默认租户被拒绝而非静默混用数据。
- 框架 `DatabaseTenant` 的 schema 解释只保留一个启用分支，后续 SaaS 化按 ADR 重新评估。
- 部署配置不再维护无实际作用的 `request` 租户解析块。

## 回滚

将目标环境的 `enabled` 改回 `true` 并配好租户注册与路由之前，多租户能力不可用；本 ADR 不承诺运行时动态路由已有实现。
