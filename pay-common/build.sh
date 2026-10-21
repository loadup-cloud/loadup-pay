#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../scripts/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: pay-common/build.sh [extra mvnd arguments]'
  echo 'Builds the shared library and its reactor dependencies without Docker or deployment.'
  exit 0
fi
prepare_java
require_command mvnd
cd "$PAY_ROOT"
exec mvnd --batch-mode clean package -pl pay-common -am -Dmaven.test.skip=true "$@"
