#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
require_command kubectl
require_command python3
require_supported_kubectl
echo 'PASS: kubectl and the selected API server are within the supported minor-version difference.'
