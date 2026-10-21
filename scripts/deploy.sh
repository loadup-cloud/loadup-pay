#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h || "${2:-}" == --help || "${2:-}" == -h ]]; then
  echo 'Usage: scripts/deploy.sh [all|application]'
  echo 'Builds JARs/images, applies only selected applications, then waits for ready endpoints.'
  echo 'For previously built images: SKIP_BUILD=1 IMAGE_TAG=<existing-tag> scripts/deploy.sh [scope]'
  exit 0
fi
[[ $# -le 1 ]] || { echo 'Expected one application name or all.' >&2; exit 1; }
scope=${1:-all}
select_apps "$scope"
require_command kubectl
require_command python3
require_command docker
if [[ "${SKIP_BUILD:-0}" == 1 && -z "${IMAGE_TAG:-}" ]]; then
  echo 'SKIP_BUILD=1 requires the existing IMAGE_TAG.' >&2
  exit 1
fi
prepare_image_tag
kubectl --context "$KUBE_CONTEXT" --request-timeout=10s get --raw=/readyz >/dev/null
if [[ "${SKIP_BUILD:-0}" != 1 ]]; then
  "$PAY_ROOT/scripts/build.sh" "$scope"
fi
for app in "${SELECTED_APPS[@]}"; do
  docker --context "$DOCKER_CONTEXT" image inspect "$IMAGE_PREFIX/$app:$IMAGE_TAG" --format '{{.Id}}' >/dev/null
done
echo "Deploying $scope to $KUBE_CONTEXT/loadup-pay with IMAGE_TAG=$IMAGE_TAG"
kubectl --context "$KUBE_CONTEXT" --request-timeout=15s create --dry-run=client --validate=false -f "$PAY_ROOT/deploy/pay.yml" -o json |
  python3 "$PAY_ROOT/scripts/render-deploy.py" "$scope" "$IMAGE_PREFIX" "$IMAGE_TAG" |
  kubectl --context "$KUBE_CONTEXT" apply -f -
"$PAY_ROOT/scripts/verify.sh" "$scope"
