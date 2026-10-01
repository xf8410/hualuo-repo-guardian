#!/usr/bin/env bash
# check-artifact-no-zip-in-zip.sh —— 红线七：artifact 不嵌 zip
# 检查 CI 产出的 artifact 不超 5 MB（自检日志）、不含 zip-of-zip、必须有 self-check.log。

set -uo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "为什么查：artifact 16.5 MB 嵌 zip-of-zip = 配额撑爆 + 审计困难 + CI 假绿。"
echo

errors=0

# 1. 检查 logs/ 目录大小（自检日志上限 5 MB）
echo "[检查 1/4] 自检日志目录不超过 5 MB..."
if [ -d logs/ ]; then
  total_size=$(du -sb logs/ 2>/dev/null | cut -f1 || echo 0)
  total_mb=$((total_size / 1048576))
  if [ "$total_size" -gt 5242880 ]; then  # 5 MB
    echo "❌ logs/ 超过 5 MB（${total_size} 字节 = ${total_mb} MB）"
    echo "   红线：自检日志上限 5 MB；超过就是嵌了不该嵌的东西"
    errors=$((errors + 1))
  else
    echo "✅ logs/ 大小：${total_size} 字节（${total_mb} MB）"
  fi
else
  echo "⚠️  没有 logs/ 目录（如果是 CI 跑：构建时还没生成）"
fi

# 2. 检查 logs/ 里没有 zip-of-zip
echo "[检查 2/4] logs/ 不含 zip-of-zip..."
if [ -d logs/ ]; then
  zip_files=$(find logs/ -type f -name '*.zip' 2>/dev/null || true)
  if [ -n "$zip_files" ]; then
    echo "❌ logs/ 里有 zip 文件（不允许）："
    printf '%s\n' "$zip_files"
    errors=$((errors + 1))
  fi
fi

# 3. 必备 self-check.log
echo "[检查 3/4] 必须有 self-check.log..."
if [ -d logs/ ] && [ ! -f logs/self-check.log ]; then
  echo "❌ 没有 logs/self-check.log（必备 artifact）"
  errors=$((errors + 1))
fi

# 4. 必备字段（self-check.log 里的关键字段）
echo "[检查 4/4] self-check.log 必备字段..."
if [ -f logs/self-check.log ]; then
  required_fields=("tag=" "commit_sha=" "timestamp=" "version=" "test_engine=" "test_app=" "red_lines=")
  for field in "${required_fields[@]}"; do
    if ! grep -q "^${field}" logs/self-check.log; then
      echo "❌ self-check.log 缺字段：$field"
      errors=$((errors + 1))
    fi
  done
fi

if [ "$errors" -gt 0 ]; then
  echo
  echo "❌ 红线七失败 $errors 项。"
  exit 1
fi
echo
echo "✅ 红线七 OK：artifact 规范。"
