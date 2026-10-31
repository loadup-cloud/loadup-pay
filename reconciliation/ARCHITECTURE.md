# reconciliation 架构

接入及配置见 [README](README.md)，跨服务协议与数据库基线见 [总体架构](../ARCHITECTURE.md)。

## 数据与执行

五张业务表和 Flyway 迁移源码位于 infrastructure，DO 继承 BaseDO，Mapper/TableDef 由处理器生成。
create/attach/start 是独立本地写事务；start 的原 runId 与原生任务同事务提交。执行按“只读 RR 取封存输入 → 事务外纯比较 →
本地写事务锁批次并提交全部差错/完成摘要”分段。
原生领取/租约/ACK 由 DelayedTask 管理；批次原运行和确定性结果保证业务幂等，不把元数据检查称为原生 token fencing。
同一批次不能换源重跑；更正文件/profile 应新建输入和批次。查询与计数处于同一次只读 RR 快照。

`deploy/pay.yml` 读取服务的 ConfigMap 和 Secret；健康检查或 DNS 就绪不代表真实对账通过。
本轮没有执行构建、脚本、数据库或外部金融调用。详见 [对账设计](../ARCHITECTURE.md)、[当前迁移](reconciliation-infrastructure/src/main/resources/db/migration/V20261027000000__reconciliation_core.sql)。
