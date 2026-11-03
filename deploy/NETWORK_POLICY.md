# HTTP 入站网络策略

七个 `<app>-http-ingress` NetworkPolicy 随应用清单渲染，限定目标 Pod 的 TCP 8080；不配置 Egress，保留 DNS、数据库、KMS、渠道及通知等出站路径。
策略按现有调用关系允许入站，不给业务服务增加发现 RBAC：

| 提供方 | 允许的 loadup-pay 调用方 |
| --- | --- |
| customer | product |
| product | acquire |
| limitcenter | product、payment |
| payment | acquire、limitcenter、reconciliation |
| acquire | payment |
| realtime | acquire、payment |
| reconciliation | 无直接内部调用方；受控外部集成经过网关 |

每个 peer 的 namespaceSelector 与 podSelector 放在同一项，二者同时满足；没有 `{}` 全放行项。
除 limitcenter 外，另允许 envoy-gateway-system 中 app.kubernetes.io/name=envoy 且归属 loadup-pay/loadup-pay Gateway 的代理 Pod。
网关控制器本身和其他 Gateway 的代理不在该选择器中。使用 Gateway Namespace Mode 或 mergeGateways 时，必须先调整并验证选择器。

监控允许 observability 中带 loadup.io/metrics-scraper=true 的 Pod。prometheus-values.yaml 已为 server 配置此 Pod label；已有监控实例需要同步更新。
8080 同时承载指标和业务入口，L3/L4 策略不能按 URL 分离，监控标签也不授予业务 JWT 权限。应用继续验证可信身份和租户。

策略与清单可审查，但只有支持并执行 NetworkPolicy 的网络实现才会生效。仅创建对象或看到 Pod ready 不能证明隔离。
当前只读发现 kube-system 没有返回 DaemonSet，不能据此推断 OrbStack 的执行能力；CNI 和允许/拒绝流量仍待实际验收。
2026-11-03 只读复核时 loadup-pay 的 NetworkPolicy 列表仍为空，当前运行环境尚不能证明本清单的入站规则已部署；见 [环境快照](ACCEPTANCE.md#当前环境只读快照)。
规则是叠加的，其他已有 allow-all 策略可能扩大允许范围；旧集群策略不会被 apply 自动删除。
见 [Kubernetes NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/)。

全量发布包含 Namespace、七组 NetworkPolicy/Deployment/Service，共 22 资源，单应用为 4 资源。
渲染器在输出前检查选定应用的完整策略，将策略排序在 Deployment 前，拒绝缺失、重复或扩大 peer 的配置。
不存在对 Kubernetes 全部资源的事务性 apply；任一发布失败都需检查实际已应用对象，再使用匹配的归档清单恢复。

回归源码检查策略完整性、选择器边界和输出顺序；未运行测试或策略部署。实际验收需覆盖允许调用、无权 Pod、其他 namespace、
同 namespace 伪造/缺失标签、其他 Gateway、Prometheus、节点探针，以及滚动期间的服务连通。标签不是密码学身份，namespace 写权限须受控。
