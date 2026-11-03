# HTTP / Gateway / 线程模型验收记录

状态：NOT_RUN。本模板不包含执行结果。

| 环境事实 | 记录 |
| --- | --- |
| 日期、执行者、获准范围 | 待填 |
| 集群/context/CNI、Kubernetes/kubectl/Helm 版本 | 待填 |
| JDK/Boot、应用 Git SHA、镜像 digest | 待填 |
| 各应用配置/环境 Secret 版本 | 待填；不填写密钥值 |
| Gateway API/Envoy Gateway、证书版本 | 待填 |
| PostgreSQL/JDBC、schema/迁移列表 | 待填；不填写凭证 |
| JVM/堆、Pod 数、资源限制、所有连接池与并发预算 | 待填 |
| A/B 实际线程属性、profile 与覆盖来源 | 待填 |
| 数据集、负载工具/版本、预热/时长/请求分布 | 待填 |
| 冻结的通过阈值与停止条件 | 待填 |

| 必需验收项 | 结果 | 原始证据/用例 | 未解决项 |
| --- | --- | --- | --- |
| 窄模块 clean 构建/契约与边界测试 | NOT_RUN | 待填 | 待填 |
| 44 项 HTTP 操作与本地/集群地址覆盖 | NOT_RUN | 待填 | 待填 |
| JWT/租户/权限/受众、凭证轮换 | NOT_RUN | 待填 | 待填 |
| PostgreSQL 初始化、隔离、事务/租约/幂等/未知结果 | NOT_RUN | 待填 | 待填 |
| Native ACK、网关原文/路径/头、无隐式重试 | NOT_RUN | 待填 | 待填 |
| CNI 允许/拒绝、DNS、多副本、滚动排空/回退 | NOT_RUN | 待填 | 待填 |
| TLS/证书轮换、Gateway/Route/Policy/xDS 状态 | NOT_RUN | 待填 | 待填 |
| A/B 上下文/异常/调度正确性 | NOT_RUN | 待填 | 待填 |
| A/B 吞吐、P95/P99、语义错误、CPU/内存/GC | NOT_RUN | 待填 | 待填 |
| DB/HTTP 获取等待、饱和与业务并发/队列 | NOT_RUN | 待填 | HTTP 池采集源码已补；需附真实指标、超时及饱和证据 |
| 七服务业务链路及恢复/发布回退 | NOT_RUN | 待填 | 待填 |

结论：INCOMPLETE。虚拟线程默认仍关闭；不得把模板、渲染成功、历史结果或部分通过代替本次全量证据。
