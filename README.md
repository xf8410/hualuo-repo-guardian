# hualuo-repo-guardian

> **花落全自动仓库工具·硬守门版**
> 重构自 `xf8410/hualuo-repo-tool` —— 加了 8 条红线守门（CI "假绿"/ 单功能 PR / artifact 不嵌 zip / 每 tag 单 feature 等），把所有 AI 之前犯的错都做成强制 CI 检查。

## 核心承诺

- 🔒 **硬守门不改**：8 条红线由 CODEOWNERS 锁死，AI 不许改 CI 绕过它们
- 🎯 **单功能单发布**：一个 PR 一个 feature，一个 tag 一个 release
- 📦 **artifact 不嵌 zip**：自检日志独立可读，禁 zip-of-zip
- 📋 **必须 self-check.log**：每次 CI 跑必产 self-check.log
- 🚫 **禁止假绿**：禁止 `continue-on-error` / `tail -n 截断` / `|| true` 等吞错机制

## 硬守门清单（详见 docs/HARD-GATES.md）

| 红线 | 含义 |
|---|---|
| 红线一 | 禁止构建时改源码（已存在） |
| 红线二 | 禁止整文件读内存（已存在） |
| 红线三 | 单文件不许超 999 行（已存在） |
| 红线四 | 版本号只能有一个来源（已存在） |
| 🆕 红线五 | 禁止假绿机制（核心防御） |
| 🆕 红线六 | PR 必须单功能 |
| 🆕 红线七 | artifact 不嵌 zip |
| 🆕 红线八 | 每 tag 单次 CI + 单 feature release |

## 项目说明（继承自 hualuo-repo-tool）

仓库自动化工作台（Kotlin/Android）：全格式流式上传（进度条/断点续传/不闪退）、带密码解压（zip4j AES/7z/tar.gz）、防 zip-slip/zip 炸弹、前台服务标准模板。

## 本地预检

commit 前手动跑一次：

```bash
bash scripts/pre-commit-check.sh
```

## 详细文档

- `docs/HARD-GATES.md` —— 所有硬守门规则
- `docs/LESSONS.md` —— AI 之前犯过的 12 个错，对应红线的来源
- `docs/FOUNDATION.md` —— 项目地基（继承）
- `docs/SANDBOX-LOGIC.md` —— 沙盒逻辑（继承）
- `docs/TOOL-CONTRACTS.md` —— 工具契约（继承）
- `docs/UPSTREAM.md` —— 上游对照（继承）
- `docs/DECISIONS.md` —— 决策账（继承）
