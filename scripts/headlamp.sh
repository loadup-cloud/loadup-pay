#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-open}" in
  install)
    kubectl --context orbstack apply -f deploy/headlamp.yml
    kubectl --context orbstack -n headlamp rollout status deployment/headlamp --timeout=300s
    ;;
  token)
    kubectl --context orbstack -n headlamp create token pay-operator --duration=8h
    ;;
  open)
    echo 'Headlamp: http://127.0.0.1:14466 (keep this process running)'
    kubectl --context orbstack -n headlamp port-forward --address=127.0.0.1 service/headlamp 14466:80
    ;;
  *) echo 'Usage: headlamp.sh {install|token|open}' >&2; exit 1 ;;
esac
