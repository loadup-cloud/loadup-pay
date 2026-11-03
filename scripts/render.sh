#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: IMAGE_TAG=<tag> CONFIG_VERSION=<version> SECRET_VERSION=<version> scripts/render.sh [all|application]'
  echo 'Renders a reviewable manifest; never builds images or applies resources.'
  exit 0
fi
[[ $# -le 1 ]] || { echo 'Expected one application name or all.' >&2; exit 1; }
scope=${1:-all}
select_apps "$scope"
require_config_versions
[[ -n "${IMAGE_TAG:-}" ]] || { echo 'Rendering requires an explicit IMAGE_TAG.' >&2; exit 1; }
prepare_image_tag
require_command kubectl
require_command python3
kubectl --context "$KUBE_CONTEXT" --request-timeout=15s create --dry-run=client --validate=false -f "$PAY_ROOT/deploy/pay.yml" -o json |
  python3 "$PAY_ROOT/scripts/render-deploy.py" "$scope" "$IMAGE_PREFIX" "$IMAGE_TAG" "$CONFIG_VERSION" "$SECRET_VERSION"
