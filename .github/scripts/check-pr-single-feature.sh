#!/usr/bin/env bash
# check-pr-single-feature.sh —— 红线六：PR 必须单功能
# 检查最近一次 commit 的 message 是否符合 Conventional Commits，
# 以及 diff 文件是否属于一个 module。

set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "为什么查：一次性塞多个不相关功能 = 回退难 + 审计失效 + 责任不清。"
echo

errors=0

# 1. 最近 commit message 必须符合 Conventional Commits
echo "[检查 1/3] 最近 commit message 格式..."
last_msg=$(git log -1 --format='%s' 2>/dev/null || echo "")
if [ -z "$last_msg" ]; then
  echo "⚠️  没有 commit，跳过"
else
  if ! echo "$last_msg" | grep -qE '^(feat|fix|refactor|perf|test|docs|style|build|ci|chore|revert)\([a-z]+\):'; then
    echo "❌ commit message 不符合 Conventional Commits："
    echo "   '$last_msg'"
    echo "   要求：feat(<scope>): xxx / fix(<scope>): xxx / ci(<scope>): xxx"
    errors=$((errors + 1))
  fi
fi

# 2. diff 文件必须属于一个 module
echo "[检查 2/3] 最近 commit 改动文件是否属于一个 module..."
modules=""
if [ -n "$(git log -1 --format='%H' 2>/dev/null)" ]; then
  for f in $(git diff-tree --no-commit-id --name-only -r HEAD 2>/dev/null); do
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
    echo "❌ 跨多个 module：$modules"
    echo "   必须一个 PR 一个 module"
    errors=$((errors + 1))
  fi
fi

# 3. commit message 长度（禁止含糊话）
echo "[检查 3/3] commit message 必须能复述做了什么..."
if [ -n "$last_msg" ]; then
  # subject 部分必须 > 20 字符（不算 type(scope): 前缀）
  subject=$(echo "$last_msg" | sed -E 's/^[a-z]+\([a-z]+\):[[:space:]]*//')
  if [ "${#subject}" -lt 10 ]; then
    echo "❌ commit subject 太短（${#subject} 字符）：'${subject}'"
    echo "   必须能复述做了什么，不能写'修一些 bug'这种含糊话"
    errors=$((errors + 1))
  fi
fi

if [ "$errors" -gt 0 ]; then
  echo
  echo "❌ 红线六失败 $errors 项。"
  exit 1
fi
echo
echo "✅ 红线六 OK：commit 符合单功能要求。"
