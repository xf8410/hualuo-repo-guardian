#!/usr/bin/env bash
# pre-commit-check.sh —— 本地预检，对应 CI 的红线一/二/三/四/五/六/七/八
# 用法：在 commit 前手动跑一次，确保不会推到 GitHub 才发现问题。
#
# 这个脚本**不替代** CI——CI 是最终守门。本地预检只是让你少一次推后被红的尴尬。

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "=== 本地预检（hualuo-repo-guardian）==="
echo

# 红线一：禁止 sed/python3 改源码
echo "[红线一] 检查 .kt/.kts 是否有 sed/python3 改源码..."
if grep -RIn -E 'sed[[:space:]]+-i|python3?[[:space:]]+[^|;]*>+' app engine --include='*.kt' --include='*.kts' 2>/dev/null; then
  echo "❌ 发现 sed/python3 改源码，请用 edit_file 替代"
  exit 1
fi
echo "✅ 红线一 OK"

# 红线二：禁止 readAllBytes / readBytes / toByteArray
echo "[红线二] 检查整文件读入内存..."
if grep -RIn -E 'readAllBytes|readBytes\(\)|toByteArray\(\)' app engine --include='*.kt' --exclude-dir=test 2>/dev/null; then
  echo "❌ 出现整文件读入内存写法，请改用 streamingCopy"
  exit 1
fi
echo "✅ 红线二 OK"

# 红线三：单文件不许超 999 行
echo "[红线三] 检查单文件超过 999 行..."
over=""
while IFS= read -r f; do
  n=$(wc -l < "$f")
  if [ "$n" -gt 999 ]; then
    over="$over$n  $f"$'\n'
  fi
done < <(find app engine -type f \( -name '*.kt' -o -name '*.kts' \) 2>/dev/null)
if [ -n "$over" ]; then
  echo "❌ 以下文件超过 999 行："
  printf '%s' "$over"
  exit 1
fi
echo "✅ 红线三 OK"

# 红线四：版本号单一来源
echo "[红线四] 检查版本号单源..."
if [ ! -f version.properties ]; then
  echo "❌ 找不到 version.properties"
  exit 1
fi
v_file=$(grep '^versionName=' version.properties | cut -d= -f2 | tr -d '[:space:]')
if [ -z "$v_file" ]; then
  echo "❌ version.properties 没写 versionName"
  exit 1
fi
stray=$(grep -RIn -E 'versionName[[:space:]]*=[[:space:]]*"' app engine --include='*.kt' --include='*.kts' 2>/dev/null || true)
if [ -n "$stray" ]; then
  echo "❌ 源码里还有写死的版本号："
  printf '%s\n' "$stray"
  exit 1
fi
echo "✅ 红线四 OK（version=${v_file}）"

# 红线五：禁止假绿机制（本地只能检查一部分，CI 是最终守门）
echo "[红线五] 检查 commit 是否有 continue-on-error / tail 截断..."
# 只检查最近 10 个 commit 的 diff
recent_commits=$(git log --oneline -10 | cut -d' ' -f1)
for c in $recent_commits; do
  msg=$(git log -1 --format='%s' "$c")
  if echo "$msg" | grep -qE 'continue-on-error|tail -n|head -n|set \+e'; then
    echo "❌ commit ${c:0:7} 包含假绿机制关键字：$msg"
    exit 1
  fi
done
echo "✅ 红线五 OK（仅检查最近 10 个 commit）"

# 红线六：commit message 格式（local）
echo "[红线六] 检查最近 commit message 格式..."
last_msg=$(git log -1 --format='%s' 2>/dev/null || echo "")
if [ -n "$last_msg" ]; then
  if ! echo "$last_msg" | grep -qE '^(feat|fix|refactor|perf|test|docs|ci|chore)\([a-z]+\):'; then
    echo "⚠️  最近 commit message 不符合 Conventional Commits："
    echo "   $last_msg"
    echo "   建议：feat(engine): xxx / fix(app): xxx / ci(workflow): xxx 等"
    # 不强制退出——commit 已经发生——只警告
  else
    echo "✅ 红线六 OK"
  fi
fi

# 红线七：artifact 不嵌 zip（只检查 logs/ 目录，如果存在）
echo "[红线七] 检查本地日志目录是否嵌 zip..."
if [ -d logs/ ]; then
  if find logs/ -type f -name '*.zip' | grep -q .; then
    echo "❌ logs/ 里有 zip 文件，请改成扁平结构"
    exit 1
  fi
  total_size=$(du -sb logs/ 2>/dev/null | cut -f1 || echo 0)
  if [ "$total_size" -gt 5242880 ]; then  # 5 MB
    echo "⚠️  logs/ 超过 5 MB（${total_size} 字节），CI 会红"
  fi
fi
echo "✅ 红线七 OK"

# 红线八：tag 单 feature（不在本地检查——tag 是 push 后才有）
echo "[红线八] tag 单 feature 由 CI 检查，本地跳过"

echo
echo "=== 本地预检完成 ==="
echo "提示：CI 会再跑一遍全量检查，绿色才算过。"
