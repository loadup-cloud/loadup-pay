> 2026-11-04：管理路由已改为 `/api/merchantmng` → merchantmng、`/api/productmng` → productmng。merchantcenter/productcenter 不公开；以下历史部署记录不代表新结构已部署。

# Gateway API + Envoy Gateway

默认平台入口使用 Gateway API，控制器固定为 Envoy Gateway v1.9.3，对应 Gateway API v1.6.1。
版本核对日期 2026-11-03；该系列支持 Kubernetes 1.33–1.36，见 [兼容矩阵](https://gateway.envoyproxy.io/news/releases/matrix/) 和
[v1.9.3 发布说明](https://gateway.envoyproxy.io/news/releases/notes/v1.9.3/)。不覆盖控制器配套的 Envoy Proxy 镜像版本。

2026-11-03 只读复核：本地 orbstack 服务端为 v1.36.5+orb1，客户端已升级为 v1.37.2；先前的客户端版本差距已解决。
部署脚本现检查客户端与服务端相差不超过一个 minor，规则见 [Kubernetes 版本差距政策](https://kubernetes.io/releases/version-skew-policy/#kubectl)。
2026-11-03 已按用户指令在 orbstack 部署控制器与 Pay 网关，复用现有兼容 CRD；本地入口为 pay.loadup.local，使用自签名证书。
控制器、代理、策略与只读连通性检查结果见下方“本地部署结果”；未执行单元测试、金融请求或完整业务验收。

## 平台安装与权限

控制器、CRD、GatewayClass 属于平台管理，不打包进 Java SDK。下列命令供有权限的发布者审查后执行。
当前集群已有 Gateway API v1.6.1 与 Envoy 扩展 CRD，控制器 release 为 envoy-gateway-system/eg；
Pay chart release 为 loadup-pay/loadup-pay-gateway。再次部署前先核对现有 release，不重新接管共享 CRD。
已有集群共享 CRD 时先核对版本、channel、所有者和其他消费者，不自动接管它们。

```bash
# 在 loadup-pay 根目录；check_kubernetes.py 只读查询版本。
python3 scripts/check_kubernetes.py
source deploy/gateway/versions.env
mkdir -p deploy/gateway/rendered

# 分离安装 Standard channel 的 Gateway API 和 Envoy 扩展 CRD。
helm template eg-crds oci://docker.io/envoyproxy/gateway-crds-helm \
  --version "$ENVOY_GATEWAY_VERSION" \
  --set crds.gatewayAPI.enabled=true --set crds.gatewayAPI.channel=standard \
  --set crds.envoyGateway.enabled=true > deploy/gateway/rendered/crds.yaml

# 审查渲染结果后由平台发布者执行。
kubectl --context orbstack apply --server-side -f deploy/gateway/rendered/crds.yaml
helm upgrade --install eg oci://docker.io/envoyproxy/gateway-helm \
  --version "$ENVOY_GATEWAY_VERSION" --namespace envoy-gateway-system \
  --create-namespace --set crds.enabled=false --skip-crds --kube-context orbstack --wait --timeout 5m
```

使用官方 chart 的 RBAC，不给业务 Pod 增加 Kubernetes API 权限。业务清单已关闭 ServiceAccount token 自动挂载。
单独管理 CRD 与 chart-templated safe-upgrades policy 的规则见 [官方 Helm 安装说明](https://gateway.envoyproxy.io/v1.9/install/install-helm/)。
本版本的 CRD 通过 chart dependency 管理，单独使用 --skip-crds 不足以禁用该 dependency；复用已有兼容 CRD 时显式设置 crds.enabled=false。
云平台拥有兼容 CRD 时遵循该分支的 safeUpgradePolicy 设置；不要把 --skip-crds 当成禁用所有集群级 chart 资源。

## Pay 入口配置

本目录是只包含原生 Gateway API/Envoy 扩展资源的本地 Helm chart，不包含控制器、业务镜像或证书数据。
先按 [配置发布说明](../CONFIGURATION.md) 准备应用与 namespace，再准备有效的 TLS Secret、域名和 LoadBalancer。

```bash
helm template loadup-pay-gateway ./deploy/gateway --namespace loadup-pay \
  --kube-version 1.36.5 --set-string hostname=pay.example.com \
  --set-string tlsSecret.name=loadup-pay-public-tls > deploy/gateway/rendered/pay.yaml

# 审查配置及证书引用后，由发布者执行。
helm upgrade --install loadup-pay-gateway ./deploy/gateway --namespace loadup-pay \
  --set-string hostname=pay.example.com --set-string tlsSecret.name=loadup-pay-public-tls \
  --kube-context orbstack --wait --timeout 5m
```

hostname 和 tlsSecret.name 必填，示例域名不能代替实际 DNS/证书。HTTPS 443 终止 TLS；HTTP 80 只返回 308 HTTPS 重定向，不转发业务。
调用方和渠道通知地址直接配置 HTTPS，不依靠金融客户端跟随重定向。默认两个代理副本；参数 proxy.replicas 不代表跨节点高可用已经验收。
Gateway/Route 位于 loadup-pay，代理采用默认 envoy-gateway-system namespace 和非 mergeGateways 模式；改变这些部署条件必须同步网络策略。

TLS Secret 默认同 namespace，无需 ReferenceGrant。指定 tlsSecret.namespace 时，仅跨 namespace 场景创建该 namespace 中的 ReferenceGrant，
授权来自 loadup-pay 的 Gateway 读取指定 Secret 名称；目标 namespace 和证书由平台先准备。原生 Grant 不支持限定来源 Gateway 名称，因此写 Gateway 的权限必须受控。
没有跨 namespace 后端 Service 引用，也没有宽泛的 Secret 授权。

## 本地部署结果

2026-11-03 部署到 orbstack，使用 [values-local.yaml](values-local.yaml)：

- 控制器：Envoy Gateway v1.9.3，1/1 就绪；代理 Deployment 为 2/2 就绪。
- GatewayClass Accepted=True，Gateway Accepted/Programmed=True；HTTP 与 HTTPS Listener 的 ResolvedRefs=True。
- 8 个 BackendTrafficPolicy 与 1 个 ClientTrafficPolicy 均 Accepted=True。
- 9 个 HTTPRoute 均 Accepted=True；其中 8 个 ResolvedRefs=True，reconciliation 为 BackendNotFound，等待该业务 Service 部署。
- LoadBalancer 地址为 192.168.139.2，HTTP 80 / HTTPS 443；地址是当前本地集群快照，重建后需重新查询。
- TLS Secret 为 loadup-pay/loadup-pay-local-tls。证书 SAN 为 pay.loadup.local，2026-12-03 到期；仅用于本地开发。

复用部署配置：

```bash
helm upgrade --install loadup-pay-gateway ./deploy/gateway --namespace loadup-pay \
  -f deploy/gateway/values-local.yaml --kube-context orbstack --wait --timeout 5m
```

公有证书保存在本地忽略目录 deploy/gateway/rendered/pay.loadup.local.crt；私钥由 Kubernetes Secret 保存。
rendered/ 也通过 .helmignore 排除，避免下载的控制器 chart、生成清单及证书被打入 Pay chart 的 Helm release 记录。

本机尚无 pay.loadup.local 的 DNS/hosts 记录，写入 /etc/hosts 需要管理员权限，尚未自动完成。使用浏览器前添加：

```text
192.168.139.2 pay.loadup.local
```

以下只读请求不依赖修改 hosts，并显式信任本次本地证书：

```bash
curl --noproxy '*' --resolve pay.loadup.local:443:192.168.139.2 \
  --cacert deploy/gateway/rendered/pay.loadup.local.crt https://pay.loadup.local/checkout
```

实际检查：HTTP /checkout 返回 308 至 HTTPS；HTTPS 握手及证书校验成功。/checkout 与 /checkout.js 目前由 acquire 返回 404，
绕过网关直连 acquire 的 /checkout 同样为 404；当前 acquire 镜像为 loadup-pay/acquire:20261022153640-24211，未在本次网关任务中更新业务镜像。
GET /api/internal/v1/payment/operations/detail 与 GET /actuator/health 在网关返回 404。
抽查一个代理的 connected_state=1，32 条配置更新 failure/rejected 计数均为 0；这不是双副本长期或业务压测结果。
浏览器使用自签名证书仍需按本机策略信任公有证书；本次没有修改系统证书信任设置。

随后用户授权发布七应用，现已全部更新为 release-001 并就绪，9 条 HTTPRoute 的 Accepted/ResolvedRefs 均为 True。
HTTPS /checkout 和 /checkout.js 已返回 200，先前旧镜像的静态页面 404 已解除；当前发布组合及 RustFS 检查见 [本地环境结果](../LOCAL_DEVELOPMENT.md#本次发布结果)。

## 路由与治理

| 入口 | 后端 | 请求上限 | 请求/后端超时 |
| --- | --- | --- | --- |
| POST /api/customer | customer | 64 KiB | 15s |
| POST /api/product | product | 1 MiB | 15s |
| POST /api/acquire、/api/cashier | acquire | 1 MiB | 15s |
| POST /api/payment/refunds、channel-bindings、routing-profiles、operations/recovery | payment | 1 MiB | 15s |
| POST /api/payment/channel-callbacks | payment | 64 KiB | 15s |
| POST /api/realtime/trades | realtime | 1 MiB | 15s |
| POST /api/reconciliation | reconciliation | 32 MiB | 75s |
| GET /checkout 及四个脚本/样式文件的精确路径 | acquire | 1 KiB | 10s |

没有 HTTPS catch-all、/api/internal、Actuator、纯内部 limitcenter 或未声明 HTTP 方法的后端路由。
80 端口唯一 catch-all 只重定向，不连接后端。静态文件精确匹配，不能把 /checkout.js/extra 当成静态入口。
前端连接软缓存上限设为 32 MiB，以配合最大请求缓存；它不是全局内存或请求大小限制。
代理对上传头部接收、完整接收与空闲流分别设置 10s/75s/75s 上限，超时与原应用幂等/未知结果恢复分别处理。

BackendTrafficPolicy 执行有上限的请求缓存和每路由、每代理本地 100 次/秒限流，requestsPerSecond 可显式调整。
两个代理的总限额不等于 100；这不是商户/租户配额，也不是已验证的连接池或数据库容量。缓存增加并发内存需求，必须压测后确定生产容量。
大小超限/限流保持传输 413/429，不伪造成业务受理或渠道成功 ACK。
这些是 Envoy 扩展能力，替换控制器时需重新实现和验证，不能仅保留标准 HTTPRoute。
参见 [请求缓存](https://gateway.envoyproxy.io/v1.9/tasks/traffic/request-buffering/) 和 [本地限流](https://gateway.envoyproxy.io/v1.9/tasks/traffic/local-rate-limit/)。

不设置重试策略、镜像、故障重放或 URL rewrite。路由移除请求中的 Envoy 重试/hedge/超时控制头，避免调用者开启隐式重试或改写时间预算；
Authorization、渠道验签头、traceparent/tracestate 与请求原字节不重写。
Payment 回调 Controller 显式使用 ProtocolResponse，避免通用 MVC 信封改变原生 ACK、内容类型与状态；源码回归已补，未运行。控制头行为见 [Envoy Router](https://www.envoyproxy.io/docs/envoy/v1.39.6/configuration/http/http_filters/router_filter)。
编码斜线拒绝，规范路径保持原路由；非规范路径、重复斜线及签名验证行为必须纳入验收。

访问日志仅包含时间、静态路由名、状态、字节和耗时，不记录 URL/查询串、请求头、原文、令牌或用户身份。
内部服务仍执行完整 JWT、租户与业务权限校验，不能信任未保护的网关注入身份头；审计仍在原业务负责层，原追踪上下文正常转发。
不默认加入 Spring Cloud Gateway；明确需要 BFF/聚合/Java 业务过滤时再增加独立职责。

## 升级、回退与验收

升级前重新核对兼容矩阵，审查两套 CRD、Envoy 扩展 schema、RBAC 与 chart 差异；归档旧 values、清单和 Helm revision。
先完成兼容 CRD 迁移，再更新控制器和 Pay chart，检查 GatewayClass Accepted、Gateway Programmed、每个 HTTPRoute 的 Accepted/ResolvedRefs 及策略 Accepted 条件。
CRD 不由 helm rollback 自动回退，不能直接删除或降级共享 CRD。回退先确认旧控制器能读取当前 CRD，再由发布者回退相应 Helm revision 和匹配的 Pay 配置。
证书换版由平台更新有效 Secret，需验证控制器观察及代理证书切换，不能以对象更新成功代替 TLS 握手验收。

部署前已渲染清单并通过 API Server dry-run；部署后检查结果见“本地部署结果”。IDE 未识别 Helm 原模板的 Go 语法，静态检查以 Helm 渲染后文件为准；
集群已有 CRD 不代表 IDE 已加载其 schema；IDE 的 Unknown API/resource 提示不能证明集群缺少 CRD，也不等于完成控制器验证。
scripts/tests/test_gateway_chart.py 已补语义回归源码，需 Helm 和 test-only PyYAML，未执行。
按 scripts/tests/requirements.txt 在独立测试环境准备 PyYAML，不给部署脚本增加运行依赖。

仍需验证 CNI 网络隔离、真实 JWT/TLS、回调签名原文/头/路径、伪造重试头、异常响应、大报文、多副本、证书轮换、滚动和 xDS 拒绝指标。
外部金融请求须另行获得明确授权。示例渲染和 HTTP 健康不能证明这些项目通过。
