#!/bin/bash
# 检查或修复 Java 和 POM 格式，支持附加 Maven 参数。
set -euo pipefail

cd -P "$(dirname "$0")"

install_hook() {
    local hook hook_script
    hook=$(git rev-parse --path-format=absolute --git-path hooks/pre-commit)
    hook_script=$(cat <<'HOOK'
#!/bin/bash
# LoadUp Spotless pre-commit
exec ./spotless.sh pre-commit
HOOK
    )

    if [[ -e "$hook" || -L "$hook" ]]; then
        if [[ -L "$hook" || ! -f "$hook" ]] || [[ $(cat "$hook") != "$hook_script" ]]; then
            printf '已有 pre-commit hook，请先合并现有逻辑：%s\n' "$hook" >&2
            return 1
        fi
    else
        mkdir -p "$(dirname "$hook")"
        (set -o noclobber; printf '%s\n' "$hook_script" > "$hook")
    fi
    chmod +x "$hook"
    printf '已安装 Spotless pre-commit hook：%s\n' "$hook"
}

format_staged_files() {
    local path escaped patterns=
    local index=${GIT_INDEX_FILE:-}
    local -a staged=()

    while IFS= read -r -d '' path; do
        if ! git --literal-pathspecs diff --quiet --no-ext-diff -- "$path"; then
            printf '文件同时包含未暂存改动：%s。请先处理未暂存改动，再重试提交。\n' "$path" >&2
            return 1
        fi
        [[ -f "$path" && ! -L "$path" ]] || continue
        staged+=("$path")

        # Spotless splits this option on commas and matches absolute paths as Java regexes.
        escaped="$PWD/$path"
        escaped=${escaped//\\E/\\E\\\\E\\Q}
        escaped=${escaped//,/\\E\\x2c\\Q}
        patterns+="${patterns:+,}\Q$escaped\E"
    done

    [[ -n "$patterns" ]] || return 0
    ./spotless.sh apply -B -ntp "-DspotlessFiles=$patterns" </dev/null
    if git --literal-pathspecs diff --quiet --no-ext-diff -- "${staged[@]}"; then
        return 0
    fi
    # A path-limited commit uses a temporary index and discards hook updates to the real index.
    if [[ ${index##*/} == next-index-*.lock ]]; then
        printf '已格式化指定文件。请重试本次提交，让 Git 将格式化结果同步到暂存区。\n' >&2
        return 1
    fi
    git --literal-pathspecs add -- "${staged[@]}"
}

case "${1:-}" in
    apply|check)
        action=$1
        shift
        exec mvnd "com.diffplug.spotless:spotless-maven-plugin:${action}" "$@"
        ;;
    install)
        install_hook
        ;;
    pre-commit)
        git diff --cached --name-only -z --no-renames --diff-filter=AMT -- \
            ':(glob)**/src/main/java/**/*.java' ':(glob)**/src/test/java/**/*.java' ':(glob)**/pom.xml' \
            | format_staged_files
        ;;
    *)
        echo "使用方法: $0 {apply|check|install} [Maven参数]" >&2
        exit 1
        ;;
esac
