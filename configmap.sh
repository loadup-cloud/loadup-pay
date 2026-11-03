#!/usr/bin/env bash
set -euo pipefail

# 配置公共参数
CONTEXT="orbstack"
NAMESPACE="loadup-pay"
BASE_DIR="deploy/local-config/cfg-default-sec-default"

# 应用列表
apps=(
  "customer"
  "product"
  "limitcenter"
  "payment"
  "acquire"
  "realtime"
  "reconciliation"
)

for app in "${apps[@]}"; do
  config_dir="$BASE_DIR/$app"
  echo "=========================================="
  echo "Processing application: [ $app ]"
  echo "=========================================="

  # 检查目录是否存在
  if [ ! -d "$config_dir" ]; then
    echo "⚠️ Warning: Directory $config_dir does not exist, skipping..."
    echo ""
    continue
  fi

  # 1. 先删除旧资源（因为资源是 immutable: true，无法直接 update/replace，必须先删除）
  echo "Deleting existing resources..."
  kubectl --context "$CONTEXT" -n "$NAMESPACE" delete configmap "${app}-config-cfg-default" --ignore-not-found
  kubectl --context "$CONTEXT" -n "$NAMESPACE" delete secret "${app}-secret-sec-default" --ignore-not-found
  kubectl --context "$CONTEXT" -n "$NAMESPACE" delete secret "${app}-service-tokens" --ignore-not-found

  # 2. 创建不可变的 ConfigMap
  if [ -f "$config_dir/config.env" ]; then
    echo "Creating ConfigMap: ${app}-config-cfg-default..."
    kubectl --context "$CONTEXT" -n "$NAMESPACE" create configmap "${app}-config-cfg-default" \
      --from-env-file="$config_dir/config.env" --dry-run=client -o yaml |
      kubectl patch --local -f - --type=merge -p '{"immutable":true}' -o yaml |
      kubectl --context "$CONTEXT" -n "$NAMESPACE" create -f -
  else
    echo "ℹ️ Notice: $config_dir/config.env not found, skipping ConfigMap."
  fi

  # 3. 创建不可变的 Secret
  if [ -f "$config_dir/secret.env" ]; then
    echo "Creating Secret: ${app}-secret-sec-default..."
    kubectl --context "$CONTEXT" -n "$NAMESPACE" create secret generic "${app}-secret-sec-default" \
      --from-env-file="$config_dir/secret.env" --dry-run=client -o yaml |
      kubectl patch --local -f - --type=merge -p '{"immutable":true}' -o yaml |
      kubectl --context "$CONTEXT" -n "$NAMESPACE" create -f -
  else
    echo "ℹ️ Notice: $config_dir/secret.env not found, skipping Secret."
  fi

  # 4. 创建可变的 Service Tokens Secret
  if [ -e "$config_dir/service-tokens" ]; then
    echo "Creating Secret: ${app}-service-tokens..."
    kubectl --context "$CONTEXT" -n "$NAMESPACE" create secret generic "${app}-service-tokens" \
      --from-file="$config_dir/service-tokens"
  else
    echo "ℹ️ Notice: $config_dir/service-tokens not found, skipping service-tokens."
  fi

  echo "✅ Finished $app"
  echo ""
done

echo "🎉 All applications processed successfully!"