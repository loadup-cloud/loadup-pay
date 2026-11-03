#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: scripts/dev-jwt.sh'
  echo 'Offline dev-only SERVICE JWT issuance; existing files are preserved unless RENEW=1.'
  echo 'Uses deploy/config/dev/service-token-policy.json; JWT_TTL_DAYS defaults to 30.'
  echo 'Set JWT_PRIVATE_KEY to an existing RSA private key. No key is generated automatically.'
  exit 0
fi
[[ $# == 0 ]] || { echo 'Unexpected arguments; use --help.' >&2; exit 1; }
select_environment
[[ "$APP_ENV" == dev ]] || { echo 'Offline development token issuance is restricted to APP_ENV=dev.' >&2; exit 1; }
require_command openssl
require_command python3
exec python3 "$PAY_ROOT/scripts/issue-dev-jwt.py"
