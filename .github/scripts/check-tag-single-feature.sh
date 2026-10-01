#!/usr/bin/env bash
# check-tag-single-feature.sh —— 红线八：每 tag 单 feature release
# 检查最近一次 push 的 tag 是否能对应 commit、是否在单 module 内。

set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "为什么查：每 tag 必须单 feature + 对应 commit，不允许一个 tag 塞多 feature。"
echo

errors=0

# 1. 最近 tag 必须符合 vX.Y.Z[-suffix] 格式
echo "[检查 1/3] tag 格式..."
latest_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$latest_tag" ]; then
  echo "⚠️  仓库没有 tag"
else
  if ! echo "$latest_tag" | grep -qE '^v[0-9]+\.[0-9]+\.[0-9]+(-[a-z0-9-]+)?$'; then
    echo "❌ tag 格式不对：$latest_tag"
    echo "   要求：vX.Y.Z 或 vX.Y.Z-feature / vX.Y.Z-fix"
    errors=$((errors + 1))
  fi
fi

# 2. tag 对应 commit 必须有对应的 commit message
echo "[检查 2/3] tag 对应 commit..."
if [ -n "$latest_tag" ]; then
  tag_commit=$(git rev-list -n 1 "$latest_tag" 2>/dev/null || echo "")
  if [ -n "$tag_commit" ]; then
    msg=$(git log -1 --format='%s' "$tag_commit")
    if ! echo "$msg" | grep -qE '^(feat|fix|refactor|perf)\([a-z]+\):'; then
      echo "❌ tag 对应 commit message 不像 release 类型：$msg"
      errors=$((errors + 1))
    fi
  fi
fi

# 3. tag 对应 commit 必须改了对应 module（不能跨 module）
echo "[检查 3/3] tag 对应 commit 改动文件属于一个 module..."
if [ -n "$latest_tag" ]; then
  tag_commit=$(git rev-list -n 1 "$latest_tag" 2>/dev/null || echo "")
  if [ -n "$tag_commit" ]; then
    modules=""
    for f in $(git diff-tree --no-commit-id --name-only -r "$tag_commit^!" 2>/dev/null); do
      case "$f" in
        app/*)        m="app" ;;
        engine/*)     m="engine" ;;
        docs/*)       m="docs" ;;
        .github/*|scripts/*) m="ci" ;;
        signing/*)    m="signing" ;;
        releases/*)   m="release" ;;
        *)            m="root" ;;
      esac
      modules="$modules $m"
    done
    modules=$(echo "$modules" | tr ' ' '\n' | sort -u | grep -v '^$' || true)
    module_count=$(echo "$modules" | wc -l)
    if [ "$module_count" -gt 1 ]; then
      echo "❌ 跨 module：$modules"
      errors=$((errors + 1))
    fi
  fi
fi

if [ "$errors" -gt 0 ]; then
  echo
  echo "❌ 红线八失败 $errors 项。"
  exit 1
fi
echo
echo "✅ 红线八 OK：tag 单 feature。"
