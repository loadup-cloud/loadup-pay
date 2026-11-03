#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: scripts/up.sh [all|application]'
  echo 'Validate/create configuration, then build and deploy using scripts/deploy.sh.'
  echo 'APP_ENV selects dev (default), test or prod; edit deploy/config/<APP_ENV> directly.'
  echo 'Snapshot versions are derived from content; explicit versions override them.'
  echo 'Use SKIP_BUILD=1 IMAGE_TAG=<existing-tag> to reuse existing images.'
  echo 'Does not install the gateway controller, local issuer, database or S3.'
  exit 0
fi
[[ $# -le 1 ]] || { echo 'Expected one application name or all.' >&2; exit 1; }
scope=${1:-all}
select_apps "$scope"
select_environment
require_environment_context
if [[ "${SKIP_BUILD:-0}" == 1 && -z "${IMAGE_TAG:-}" ]]; then
  echo 'SKIP_BUILD=1 requires the existing IMAGE_TAG.' >&2
  exit 1
fi
prepare_image_tag
read -r CONFIG_VERSION SECRET_VERSION < <(bash "$PAY_ROOT/scripts/configmap.sh" versions "$scope")
export CONFIG_VERSION SECRET_VERSION
require_config_versions
bash "$PAY_ROOT/scripts/configmap.sh" create "$scope"
exec bash "$PAY_ROOT/scripts/deploy.sh" "$scope"
