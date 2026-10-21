#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for app in customer product limitcenter payment acquire realtime; do
  kubectl -n loadup-pay rollout status "deployment/$app" --timeout=120s
  kubectl -n loadup-pay get endpointslices -l "kubernetes.io/service-name=$app" -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); assert any(e.get("conditions",{}).get("ready") and e.get("addresses") for s in d["items"] for e in s.get("endpoints",[])), "No ready service endpoint"'
done
log=$(mktemp)
kubectl -n loadup-pay port-forward service/acquire 18080:8080 > "$log" 2>&1 &
pf=$!
cleanup() {
  kill "$pf" 2>/dev/null || true
  wait "$pf" 2>/dev/null || true
  rm -f "$log"
}
trap cleanup EXIT
for i in {1..30}; do
  if body=$(curl --fail --silent http://127.0.0.1:18080/actuator/health/paymentRpc); then
    printf '%s\n' "$body" | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "UP"'
    echo 'PASS: acquire resolved payment through Kubernetes DNS and received the expected gRPC response.'
    exit 0
  fi
  sleep 1
done
echo 'FAIL: gRPC probe did not become UP' >&2
exit 1
