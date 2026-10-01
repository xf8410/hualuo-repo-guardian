#!/usr/bin/env bash
# check-no-fake-green.sh —— 红线五：禁止假绿机制
# CI 跑本脚本，确保 .github/workflows/ 下没有 continue-on-error / tail 截断 / || true / set +e 等假绿机制。
#
# 实现要点：
#   1. 用 awk 跳过 yaml 字符串内的内容（避免 run-name 字符串里的 || 被误判）
#   2. 跳过 yaml 注释行（以 # 开头，跳过空白后的 # 也算）
#   3. 跳过 bash 注释行（在 # 后面的内容不算代码）

set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "为什么查：CI '假绿' = run 整体绿勾，但内部某步其实失败了。"
echo "挡的机制：continue-on-error / tail-n 截断 / || true / set +e"
echo

errors=0

# 提取 yaml 文件里"不在字符串内、不在注释里"的代码行
get_code_lines() {
  awk '
    BEGIN { in_str = 0 }
    {
      line = $0
      # 整行注释直接跳过（yaml 注释是 # 开头，前面可有空白）
      stripped = line
      sub(/^[[:space:]]+/, "", stripped)
      if (substr(stripped, 1, 1) == "#") next

      out = ""
      while (length(line) > 0) {
        if (in_str) {
          ci = index(line, "\"")
          if (ci == 0) { line = ""; break }
          line = substr(line, ci + 1)
          in_str = 0
        } else {
          oi = index(line, "\"")
          if (oi == 0) { out = out line "\n"; line = ""; break }
          out = out substr(line, 1, oi - 1) "\n"
          line = substr(line, oi + 1)
          in_str = 1
        }
      }
      n = split(out, parts, "\n")
      for (i = 1; i <= n; i++) {
        if (length(parts[i]) > 0) {
          # 再去掉行内 # 后面的注释
          sub_part = parts[i]
          hash_idx = index(sub_part, "#")
          if (hash_idx > 0) sub_part = substr(sub_part, 1, hash_idx - 1)
          if (length(sub_part) > 0) print FILENAME ":" NR ":" sub_part
        }
      }
    }
  ' "$@"
}

# 1. continue-on-error：只允许 upload-artifact 用
echo "[检查 1/4] continue-on-error 仅允许 upload-artifact..."
bad=$(get_code_lines .github/workflows/*.yml | grep 'continue-on-error' | grep -v 'actions/upload-artifact' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 continue-on-error（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 2. tail -n / head -n 截断（只在代码里查）
echo "[检查 2/4] 禁止 tail -n / head -n 截断日志..."
bad=$(get_code_lines .github/workflows/*.yml | grep -E '(^|[^a-z])(tail|head)[[:space:]]+-[a-zA-Z]*n[[:space:]]+[0-9]+' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 tail/head -n 截断（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 3. || true（只在 bash 代码里查，且 true 后必须接空白/行尾/;&|）
echo "[检查 3/4] 禁止 || true 吞错误..."
bad=$(get_code_lines .github/workflows/*.yml | grep -E '\|\|[[:space:]]+true([[:space:]]*$|[[:space:]]*[;&|])' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 || true（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

# 4. set +e / set +o pipefail
echo "[检查 4/4] 禁止 set +e / set +o pipefail..."
bad=$(get_code_lines .github/workflows/*.yml | grep -E 'set[[:space:]]+\+e([[:space:]]|$)|set[[:space:]]+\+o[[:space:]]+pipefail' || true)
if [ -n "$bad" ]; then
  echo "❌ 以下行有 set +e / set +o pipefail（不允许）："
  printf '%s\n' "$bad"
  errors=$((errors + 1))
fi

if [ "$errors" -gt 0 ]; then
  echo
  echo "❌ 发现 $errors 类假绿机制，CI 红。"
  exit 1
fi
echo
echo "✅ 红线五 OK：没有假绿机制。"
