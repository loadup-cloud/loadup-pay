#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
select_apps "${1:-all}"
require_command kubectl
require_command python3
for app in "${SELECTED_APPS[@]}"; do
  kubectl --context "$KUBE_CONTEXT" -n loadup-pay rollout status "deployment/$app" --timeout=180s
  kubectl --context "$KUBE_CONTEXT" -n loadup-pay get endpointslices -l "kubernetes.io/service-name=$app" -o json | python3 -c 'import json,sys; d=json.load(sys.stdin); assert any(e.get("conditions",{}).get("ready") and e.get("addresses") for s in d["items"] for e in s.get("endpoints",[])), "No ready service endpoint"'
done
echo "PASS: ${SELECTED_APPS[*]} are ready and their Services have ready endpoints."
