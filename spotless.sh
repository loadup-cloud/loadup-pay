#!/bin/bash
# 检查或修复格式和 Java 许可证头；支持附加 Maven 参数。
set -euo pipefail

case "${1:-}" in
    apply|check)
        action=$1
        shift
        cd "$(dirname "$0")"
        exec mvnd "com.diffplug.spotless:spotless-maven-plugin:${action}" "$@"
        ;;
    *)
        echo "使用方法: $0 {apply|check} [Maven参数]" >&2
        exit 1
        ;;
esac
