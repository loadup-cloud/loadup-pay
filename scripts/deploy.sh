#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h || "${2:-}" == --help || "${2:-}" == -h ]]; then
  echo 'Usage: scripts/deploy.sh [all|application]'
  echo 'Builds JARs/images, applies only selected applications, then waits for ready endpoints.'
  echo 'Requires CONFIG_VERSION and SECRET_VERSION of existing immutable configuration snapshots.'
  echo 'For previously built images: SKIP_BUILD=1 IMAGE_TAG=<existing-tag> CONFIG_VERSION=<version> SECRET_VERSION=<version> scripts/deploy.sh [scope]'
  exit 0
fi
[[ $# -le 1 ]] || { echo 'Expected one application name or all.' >&2; exit 1; }
scope=${1:-all}
select_apps "$scope"
require_config_versions
require_command kubectl
require_command python3
require_command docker
if [[ "${SKIP_BUILD:-0}" == 1 && -z "${IMAGE_TAG:-}" ]]; then
  echo 'SKIP_BUILD=1 requires the existing IMAGE_TAG.' >&2
  exit 1
fi
prepare_image_tag
require_supported_kubectl
# Render completely before inspecting configuration or making any cluster mutation.
release_temp=$(mktemp -d)
trap 'rm -rf "$release_temp"' EXIT
bash "$PAY_ROOT/scripts/render.sh" "$scope" > "$release_temp/manifest.json"
kubectl --context "$KUBE_CONTEXT" --request-timeout=10s get --raw=/readyz >/dev/null
for app in "${SELECTED_APPS[@]}"; do
  for reference in "configmap/$app-config-$CONFIG_VERSION" "secret/$app-secret-$SECRET_VERSION"; do
    immutable=$(kubectl --context "$KUBE_CONTEXT" -n loadup-pay --request-timeout=10s get "$reference" -o 'jsonpath={.immutable}')
    [[ "$immutable" == true ]] || { echo "Release configuration must be immutable: $reference" >&2; exit 1; }
  done
  tokens_immutable=$(kubectl --context "$KUBE_CONTEXT" -n loadup-pay --request-timeout=10s get "secret/$app-service-tokens" -o 'jsonpath={.immutable}')
  [[ "$tokens_immutable" != true ]] || { echo "Projected service tokens must remain rotatable: $app-service-tokens" >&2; exit 1; }
done
if [[ "${SKIP_BUILD:-0}" != 1 ]]; then
  "$PAY_ROOT/scripts/build.sh" "$scope"
fi
for app in "${SELECTED_APPS[@]}"; do
  docker --context "$DOCKER_CONTEXT" image inspect "$IMAGE_PREFIX/$app:$IMAGE_TAG" --format '{{.Id}}' >/dev/null
done
# Retain only references, never configuration/Secret values, for a reproducible rollback.
release_dir=${RELEASE_DIR:-$PAY_ROOT/deploy/releases}
release_file="$release_dir/$scope-$IMAGE_TAG-$CONFIG_VERSION-$SECRET_VERSION.json"
mkdir -p "$release_dir"
(umask 077; cp -n "$release_temp/manifest.json" "$release_file")
cmp -s "$release_temp/manifest.json" "$release_file" || { echo 'An existing release record has different content.' >&2; exit 1; }
echo "Deploying $scope to $KUBE_CONTEXT/loadup-pay with IMAGE_TAG=$IMAGE_TAG CONFIG_VERSION=$CONFIG_VERSION SECRET_VERSION=$SECRET_VERSION"
echo "Release manifest: $release_file (not proof of successful rollout)"
kubectl --context "$KUBE_CONTEXT" apply -f "$release_file"
"$PAY_ROOT/scripts/verify.sh" "$scope"
