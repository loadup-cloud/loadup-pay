# Reconciliation 测试入口

单元测试验证账户目录、封存/批次/差错映射、微信 ALL 解析、业务幂等与协议边界；真实持久化 IT 使用 PostgreSQL、生成 Mapper、应用事务和原生任务/Task。

| 测试范围 | 当前入口及验证目标 |
|---|---|
| 手工来源导入 | ReconciliationImportPersistenceIT：File/Transfer/导入/长期引用同库回滚，完整封存原子提交，原 FAILED 重试，坏尾部拒绝，并发请求及越权拒绝 |
| 官方来源 | ReconciliationStatementStorageConverterTest 与来源编排测试：绑定版本、READY 私有引用、manual-only 拒绝、原请求/任务和丢 ACK；来源专用多 worker/对象存储失败 IT 仍需补齐 |
| 独立复核 | ReconciliationReviewPersistenceIT：head/proposal/event/永久引用共同回滚，独立审批、并发原 ACK、原差错不变；映射及领域用例覆盖旧版本和自审批拒绝 |
| 源端查证 | ReconciliationVerificationPersistenceIT：请求/原生 Task 与回执原子性，FAILED 审计重试，ACK 丢失不重复查询，租约过期旧新 worker，nullable 覆盖缺口和越权拒绝 |

可信 actor、对象字节及 payment 传输使用受控替身。app 的计数型事务替身和 MapStruct 往返不能证明 SQL 约束、回滚或锁竞争；原生临时文件引用释放后仍需检查业务长期引用保护。

执行需当前依赖、Docker 和父 POM 的 Failsafe 配置，在 Pay 根目录选择相关类：

```bash
mvnd clean verify -pl loadup-pay-reconciliation/loadup-pay-reconciliation-infrastructure \
  -Dit.test=ReconciliationImportPersistenceIT \
  -Dspotless.skip=true -Dspotbugs.skip=true
```

## 待验收

所有入口需记录当前版本实际运行结果。待补/待验收包括比较差错与完成摘要的真实回滚、官方来源专用并发、payment 源端多候选/退款 claim SQL 一致性与索引，以及真实 S3、Transfer 崩溃恢复、JWT/TLS/HTTP、渠道和多实例链路。本地 payment 或对象字节替身不能代替外部环境证明。
