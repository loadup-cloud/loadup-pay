#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
  echo 'Usage: scripts/build-framework.sh [extra mvnd arguments]'
  echo 'Clean-install the HTTP, resource-server, SAS and local KMS components and their required dependencies.'
  echo 'FRAMEWORK_DIR defaults to the sibling loadup-framework repository; tests are skipped.'
  exit 0
fi
prepare_java
require_command mvnd
framework_dir=${FRAMEWORK_DIR:-$PAY_ROOT/../loadup-framework}
[[ -f "$framework_dir/pom.xml" ]] || {
  echo 'Framework checkout is unavailable; set FRAMEWORK_DIR or explicitly use SKIP_FRAMEWORK_BUILD=1 with installed artifacts.' >&2
  exit 1
}
projects=loadup-components/integration/loadup-components-http,loadup-components/security/loadup-components-resource-server,loadup-components/security/loadup-components-authserver/loadup-components-authserver-binder-sas,loadup-components/security/loadup-components-kms/loadup-components-kms-binder-local
cd "$framework_dir"
mvnd --batch-mode clean install -pl "$projects" -am -Dmaven.test.skip=true -Dskip.spotless=true -Dskip.spotbugs=true "$@"
