# LoadUp Pay 指标与 Grafana

链路：六个 Spring Boot 应用 `/actuator/prometheus` → Prometheus → Grafana。
Loki 存储日志；现有 `otel-collector-logs.yml` 的 logs/traces pipeline 输出到 debug，没有 metrics pipeline。
本方案由 Prometheus 直接抓取应用，不需要修改已安装的 Collector，也不向 Loki 写入 metrics。

## 1. 应用

本地开发默认关闭指标注册及 Prometheus exporter，不向 localhost:4318 推送 OTLP metrics。
需要本地查看指标时，设置环境变量 `METRICS_ENABLED=true` 后重启应用。
`pay.yml` 已为六个 Kubernetes Deployment 设置此变量为 true，启用 Prometheus 抓取。
OTLP metrics exporter 在所有环境默认关闭，指标通过 Prometheus 拉取；不影响日志和 tracing 配置。

六个 bootstrap 引入 `micrometer-registry-prometheus`，版本沿用 Spring Boot BOM，开放 health 和 prometheus。
统一附加 application 标签，启用 HTTP 延迟直方图用于 P95。指标包含 Actuator 自动采集的 JVM、进程、HTTP 指标；
支付成功率、订单金额等业务指标需要另行埋点，HTTP 5xx 比例不代表支付失败率。

按项目现有方式重新构建六个应用镜像并更新部署。`pay.yml` 的 Pod 模板已声明抓取注解，指标端口沿用 8080。
镜像仍使用本地 bootstrap 标签及 `imagePullPolicy: Never`，构建后需确保目标节点镜像已更新并重建 Pod。

```bash
kubectl apply -f loadup-pay/deploy/pay.yml
kubectl -n loadup-pay rollout restart deployment/payment deployment/acquire deployment/customer deployment/limitcenter deployment/product deployment/realtime
kubectl -n loadup-pay port-forward service/payment 18080:8080
# 在另一个终端执行，应返回 Prometheus 文本格式，包含 application="payment"
curl -fsS http://localhost:18080/actuator/prometheus
```

指标端点无需业务 JWT，仅应通过集群内网访问；不要通过公网 Ingress 暴露 `/actuator/prometheus`。
如集群已启用 NetworkPolicy，允许 Prometheus 到 loadup-pay Pod 的 TCP 8080 流量。

## 2. Prometheus 抓取

使用 `prometheus-community/prometheus` Helm chart，从仓库根目录执行：

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install prometheus prometheus-community/prometheus --version 29.37.1 \
  --namespace observability --create-namespace \
  -f loadup-pay/deploy/prometheus-values.yaml \
  -f loadup-pay/deploy/prometheus-scrape.yml \
  --wait --timeout 5m
```

两个文件均为 Helm values：前者配置存储和组件，后者通过 `serverFiles.prometheus.yml` 对象配置抓取列表。
不要使用 `--set-file serverFiles.prometheus\.yml=...`，它会传入字符串并导致 chart schema 校验失败。
需要集群默认 StorageClass，或在 `prometheus-values.yaml` 指定实际 storageClass。
此抓取列表包含 Prometheus 自监控和 loadup-pay；升级已有监控实例前需要合并其余自定义抓取任务。
其他 chart（例如 kube-prometheus-stack）使用不同配置结构，不能直接复用这两个 values 文件。

Prometheus 的 ServiceAccount 需要在 loadup-pay namespace 对 pods 有 get/list/watch 权限。
抓取器需要运行在集群内，才能访问 Kubernetes API 和 Pod IP。

本 job 每 15 秒抓取每个 Running Pod 的 http 端口，添加 namespace、pod、application 标签。
若已有通用 Pod 注解抓取任务，应排除本 namespace 后再添加此 job，避免重复抓取。
若使用 Prometheus Operator，Pod 注解不会自动生效：应通过已安装 chart 支持的 additionalScrapeConfigs
配置此 job，并确认渲染后的 Prometheus 配置包含它。此处未假设 chart release 名称或安装新 CRD。

在 Prometheus Targets 中确认 `loadup-pay` job 的所有实例 UP，再执行：

```promql
up{job="loadup-pay"}
jvm_memory_used_bytes{namespace="loadup-pay",area="heap"}
http_server_requests_seconds_count{namespace="loadup-pay"}
```

HTTP 指标需要实际请求后才出现，P95 需要直方图样本；没有流量时看板部分面板无数据或 NaN 属正常情况。
HTTP 面板排除 Actuator 请求，以免健康探针和抓取流量干扰业务请求统计。

## 3. Grafana

1. 在 Connections → Data sources 添加或选择 Prometheus 数据源，URL 使用你集群中实际 Prometheus Service 地址。
2. 在 Dashboards → New → Import 上传同目录 `grafana-loadup-pay.json`。
3. 导入时选择该 Prometheus 数据源，使用 application / pod 变量筛选实例。
4. 发起实际业务请求，确认 QPS、P95 等面板出现数据。

排查：Targets 为 DOWN 时检查 Pod 是否更新、端点是否返回 200、网络策略及 RBAC；
没有 Targets 时检查配置是否加载以及 Pod 注解；Targets UP 但看板为空时检查数据源和 namespace/application/pod 标签。
Collector 日志接入 Loki 是独立链路，当前仓库的 debug exporter 不能证明日志已写入 Loki。
