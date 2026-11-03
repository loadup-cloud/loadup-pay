#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
case "${1:---help}" in
  prepare-dev)
    select_environment
    [[ "$APP_ENV" == dev ]] || { echo 'prepare-dev is restricted to APP_ENV=dev.' >&2; exit 1; }
    prepare_java
    require_command python3
    exec python3 "$PAY_ROOT/scripts/prepare-authserver-dev.py"
    ;;
  --help|-h)
    echo 'Usage: scripts/authserver.sh prepare-dev'
    echo 'Prepares a persistent RSA KeyStore and paired application credentials locally; preserves existing values.'
    echo 'Deploy after creating the dedicated PostgreSQL database: scripts/up.sh authserver, then scripts/up.sh all.'
    ;;
  *) echo 'Unknown command; use --help.' >&2; exit 1 ;;
esac
