# Reconciliation 测试源码范围

当前包含受控账户目录、封存/批次/差错 MapStruct 映射和微信 ALL 格式解析测试源码。
上层 app 包含原请求幂等、双输入门禁、原运行登记、事务外比较、任务重放与结果持久化失败顺序；adapter/rpc 包含身份与协议边界。

本轮没有编译或运行任何测试。app 的计数型事务替身不能证明真实 SQL 回滚；没有用 MapStruct 往返代替 MySQL 外键/唯一键/锁竞争。
MySQL 输入封存/原生 Transfer 与业务同库/并发/租户 IT 源码已补；比较结果的差错/完成真实回滚及所有 IT 实际运行仍待完成，真实 JWT/HTTP/gRPC/File/Transfer/S3 链路仍待验收。


新增 ReconciliationImportPersistenceIT 源码使用真实 MySQL、File/Transfer facade、生成 Mapper 与应用服务；只为外部对象字节和可信 actor 提供测试适配。
覆盖业务登记后故障使任务/双引用/导入一起回滚、封存更新后故障使输入行/批次指针/逻辑结果一起回滚、原 FAILED 重试、永久坏尾部拒绝、并发原请求和跨租户读取拒绝。
检查业务长期引用在 native 临时引用释放后仍阻止文件删除。没有运行这些用例，也不将内存对象替身视为真实 S3/网络/租约恢复验收。
