#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [[ $# == 0 || "${1:-}" == --help || "${1:-}" == -h ]]; then
  cat <<'HELP'
Usage: scripts/configmap.sh <action> [all|application] [config|secret|tokens|all]
Actions:
  check    Validate local environment files and required token files (offline).
  versions Print content-derived CONFIG_VERSION and SECRET_VERSION (offline).
  create   Create immutable snapshots and mutable token Secrets; identical objects are reused.
  update   Publish snapshots under NEW version names (same safety checks as create).
  tokens   Explicitly rotate mutable service-token Secrets from local files.
  status   Show resource names, existence and immutability; never values.
  delete   Delete unreferenced resources; final argument selects type (default: config).

APP_ENV selects dev (default), test or prod; CONFIG_DIR defaults to deploy/config/<APP_ENV>.
create/update derive immutable versions from content unless explicitly supplied.
status/delete require explicit CONFIG_VERSION and SECRET_VERSION.
KUBE_CONTEXT defaults to orbstack; namespace is loadup-pay.
create/update refuse changed existing snapshots or tokens; use tokens for explicit token rotation.
delete checks workloads (including historical ReplicaSets) and RELEASE_DIR archives.
HELP
  exit 0
fi
[[ $# -le 3 ]] || { echo 'Too many arguments; use --help.' >&2; exit 1; }
action=$1
select_apps "${2:-all}"
case "$action" in
  check|versions|create|update|tokens|status|delete) ;;
  *) echo 'Unknown action; use --help.' >&2; exit 1 ;;
esac
if [[ "$action" != delete && $# -gt 2 ]]; then
  echo 'Resource selection is only supported for delete.' >&2
  exit 1
fi
select_environment
if [[ "$action" == status || "$action" == delete ]]; then
  require_config_versions
fi
export KUBE_CONTEXT
require_command python3
case "$action" in
  check|versions) ;;
  *) require_environment_context; require_command kubectl; require_supported_kubectl ;;
esac
exec python3 "$PAY_ROOT/scripts/manage-config.py" "$action" "${3:-config}" "${SELECTED_APPS[@]}"
