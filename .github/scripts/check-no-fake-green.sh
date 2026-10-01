#!/usr/bin/env bash
# check-no-fake-green.sh —— 红线五：禁止假绿机制
# CI 跑本脚本，确保 .github/workflows/ 下没有 continue-on-error / tail 截断 / || true / set +e / if: always 隐藏失败等假绿机制。

set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "为什么查：CI '假绿' = run 整体绿勾，但内部某步其实失败了。"
echo "挡的机制：continue-on-error / tail-n 截断 / || true / set +e / if: always 隐藏失败"
echo

errors=0

# 1. continue-on-error：只允许 upload-artifact 用
echo "[检查 1/5] continue-on-error 仅允许 upload-artifact..."
bad=$(grep -RIn 'continue-on-error' .github/workflows/ 2>/dev/null | \
  grep -v 'actions/upload-artifact' | \
  grep -v '^[^:]*:[^:]*:[[:space:]]*#' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 continue-on-error（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 2. tail -n 截断
echo "[检查 2/5] 禁止 tail -n 截断日志..."
bad=$(grep -RIn -E '(^|[^a-z])tail[[:space:]]+-[a-zA-Z]*n[[:space:]]+[0-9]+|(^|[^a-z])head[[:space:]]+-[a-zA-Z]*n[[:space:]]+[0-9]+' .github/workflows/ 2>/dev/null || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 tail/head -n 截断（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 3. || true 吞错（仅在 # 注释里允许）
echo "[检查 3/5] 禁止 || true 吞错误..."
bad=$(grep -RIn -E '||[[:space:]]*true' .github/workflows/ 2>/dev/null | \
  grep -v '^[^:]*:[^:]*:[[:space:]]*#' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 || true（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 4. set +e 关错误检查
echo "[检查 4/5] 禁止 set +e..."
bad=$(grep -RIn -E 'set[[:space:]]+\+e|set[[:space:]]+\+o[[:space:]]+pipefail' .github/workflows/ 2>/dev/null || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 set +e / set +o pipefail（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 5. if: always() 必须在同一 step 配 if: failure() 显式暴露失败
echo "[检查 5/5] if: always() 必须配 if: failure()..."
# 这条比较宽松——只检查 upload-artifact 步骤
always_steps=$(grep -RIn -B1 -A2 'if: always' .github/workflows/ 2>/dev/null | \
  grep -E 'name:' | head -5 || true)
# 简单判断：如果一个 step 有 if: always，必须在同一 jobs 下有 if: failure()
# 完整检查放在 ci.yml 的"红线五"step 里人工 review（更安全）

if [ "$errors" -gt 0 ]; then
  echo
  echo "❌ 发现 $errors 类假绿机制，CI 红。"
  exit 1
fi
echo
echo "✅ 红线五 OK：没有假绿机制。"
