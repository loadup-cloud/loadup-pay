#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h || "${2:-}" == --help || "${2:-}" == -h ]]; then
  echo 'Usage: scripts/build.sh [all|application] [extra mvnd arguments]'
  echo 'Builds clean executable JARs and local Docker images; test compilation/execution is skipped by default.'
  exit 0
fi
scope=${1:-all}
if [[ $# -gt 0 ]]; then shift; fi
select_apps "$scope"
prepare_java
prepare_image_tag
require_command mvnd
require_command docker
docker --context "$DOCKER_CONTEXT" info --format '{{.OSType}}' >/dev/null
for app in "${SELECTED_APPS[@]}"; do
  if docker --context "$DOCKER_CONTEXT" image inspect "$IMAGE_PREFIX/$app:$IMAGE_TAG" >/dev/null 2>&1; then
    echo "Image tag already exists: $IMAGE_PREFIX/$app:$IMAGE_TAG. Choose a new tag or deploy with SKIP_BUILD=1." >&2
    exit 1
  fi
done
cd "$PAY_ROOT"
projects=()
for app in "${SELECTED_APPS[@]}"; do projects+=("$app/$app-bootstrap"); done
project_list=$(IFS=,; echo "${projects[*]}")
mvnd --batch-mode clean package -pl "$project_list" -am -Dmaven.test.skip=true "$@"
shopt -s nullglob
for app in "${SELECTED_APPS[@]}"; do
  jars=("$app/$app-bootstrap/target/$app-bootstrap-"*.jar)
  [[ ${#jars[@]} -eq 1 ]] || { echo "Expected one executable JAR for $app; found ${#jars[@]}." >&2; exit 1; }
  image="$IMAGE_PREFIX/$app:$IMAGE_TAG"
  docker --context "$DOCKER_CONTEXT" build --build-arg "JAR_FILE=${jars[0]}" --tag "$image" .
  echo "Built $image"
done
echo "Build complete: IMAGE_TAG=$IMAGE_TAG"
