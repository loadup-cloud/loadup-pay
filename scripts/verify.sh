#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for app in customer product limitcenter payment acquire realtime; do
  kubectl -n loadup-pay rollout status "deployment/$app" --timeout=120s
  kubectl -n loadup-pay get endpointslices -l "kubernetes.io/service-name=$app" -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); assert any(e.get("conditions",{}).get("ready") and e.get("addresses") for s in d["items"] for e in s.get("endpoints",[])), "No ready service endpoint"'
done
echo 'PASS: all six deployments are ready and their Services have ready endpoints.'
