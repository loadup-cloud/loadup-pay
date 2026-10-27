#!/usr/bin/env bash
# Shared settings for local application builds and OrbStack deployments.
PAY_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PAY_APPS=(customer product limitcenter payment acquire realtime reconciliation)
KUBE_CONTEXT=${KUBE_CONTEXT:-orbstack}
DOCKER_CONTEXT=${DOCKER_CONTEXT:-orbstack}
IMAGE_PREFIX=${IMAGE_PREFIX:-loadup-pay}

select_apps() {
  case "$1" in
    all) SELECTED_APPS=("${PAY_APPS[@]}") ;;
    customer|product|limitcenter|payment|acquire|realtime|reconciliation) SELECTED_APPS=("$1") ;;
    *) echo "Unknown application: $1 (expected all or ${PAY_APPS[*]})" >&2; exit 1 ;;
  esac
}

require_command() {
  command -v "$1" >/dev/null || { echo "Required command is unavailable: $1" >&2; exit 1; }
}

prepare_java() {
  if command -v mise >/dev/null; then
    JAVA_HOME=$(cd "$PAY_ROOT" && mise where java)
    export JAVA_HOME
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  require_command java
  local major
  major=$(java -version 2>&1 | awk -F '"' 'NR == 1 {split($2, parts, "."); print parts[1]}')
  [[ "$major" == 25 ]] || { echo 'Java 25 is required; install the pinned mise runtime or set JAVA_HOME.' >&2; exit 1; }
}

prepare_image_tag() {
  IMAGE_TAG=${IMAGE_TAG:-$(date +%Y%m%d%H%M%S)-$$}
  [[ "$IMAGE_TAG" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo 'Invalid IMAGE_TAG.' >&2; exit 1; }
  export IMAGE_TAG IMAGE_PREFIX DOCKER_CONTEXT KUBE_CONTEXT
}
