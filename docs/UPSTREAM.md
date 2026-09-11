# 上游对账：newo-ether/Agora（原版）↔ xf8410/Agora-Workbench（魔改版）

> 2026-09-11 实查。原版：<https://github.com/newo-ether/Agora>（**MIT**，Kotlin，357★/67fork/37 open issues，默认分支 `master`，最后提交 09-06，发布 2.1.0 @ 09-05，有 F-Droid + Google Play + 文档站）。
> **重大更正**：`xf8410/Agora-Workbench` **不是 GitHub 网络意义上的 fork**（跨仓 compare 返回 404）——它是"克隆后另立新仓"，因此 **上游的修复一条都同步不过来**。这是很多"我们的坑"的真实来源。

## 一、目录级对账（树 SHA 相同 = 内容完全没动过）
**未动（纯上游货）**：`.gitmodules`(llama.cpp+termux/proot) `build-proot.sh`(9520B) `gradlew` `gradlew.bat` `gradle.properties` `settings.gradle.kts` `LICENSE` `thirdparty/` `app/src/fdroid/.../ProotNative.kt`(573B)
**已分叉**：`app/` `assets/` `docs/` `scripts/` `server/` `.github/` `.gitignore` `build.gradle.kts` `mkdocs.yml`(16,074 vs 上游 17,600) `ARCHITECTURE.md`(54,353 vs 上游 28,671) `README.md`(18,718 vs 上游 5,383)
**上游有、我们整个丢了**：`config/`（**含 Kotlin 源码行数门禁**）、`development/`（内部契约+文档维护策略）
**我们独有（自研增量）**：`apk-probe/` `README_CN.md` `ci-failure-summary.txt` `probe-root.txt` `scripts/apply_*.py` 手术刀群

## 二、坑位清单（本轮实锤，按危害排序）

### 坑1 · 沙盒"下载语言包报错/马上成功/无错误码"＝**缺上游的 Alpine provider 解析层**
上游 `app/src/fdroid/java/.../sandbox/` 是 **8 个文件**，我们只有 **3 个**。我们完全没有：
| 上游文件 | 字节 | 作用 |
|---|---|---|
| `AlpinePackageIndex.kt` | 6,930 | 解 `APKINDEX.tar.gz`，建 `provides` **虚拟 provider 表**（`so:`/别名按 priority 归一）+ `compareAlpineVersions`（`-r` 修订、`_pre`/`alpha`/`rc` 权重）+ `collectAlpinePackageChanges` 依赖闭包 |
| `AlpinePackageMetadataStore.kt` | 5,764 | 索引缓存/落盘 |
| `ProotRootfsArchiveExtractor.kt` | 2,099 | rootfs 解包 |
| `SandboxPathResolver.kt` / `SandboxVirtualPaths.kt` | 3,670 / 847 | 路径与虚拟路径归一 |

Alpine 里 **`python` 不是一个包，是个虚拟 provider**（真包叫 `python3`）。没有这张表，`apk add python` 要么报"无法满足"、要么 apk 自己换 provider 而我们的代码把"进程退出码为 0"当"装成功了"→ **正是主人看到的"马上成功 + 环境错误/下载失败 + 没有错误码"**。上游修这刀的 commit：`c880f379` "Resolve Alpine virtual providers consistently for package operations"（09-05）。
**地基处置**：①照上游结构把沙盒拆成 8 块（上游已经替我们设计好了拆法，不自己发明）②补 provider 解析 + 真版本比较 ③**安装成功判据 = 复验 `apk info` 里有这个包且版本≥期望**，不是"命令跑完了" ④任何失败必须带 **stderr 原文 + 退出码 + 包名**（对齐 R7）。

### 坑2 · A8 三个 UI bug = **我们拆掉了上游已经解决它的组件族**
上游 `ui/chat/message/` 有 **29 个文件**，我们只剩 **16 个**，并且换成了自研的 `RecomposeSafeMarkdown.kt`。上游有而我们没有的关键件：
- `GenerationErrorBar.kt`(5,545) ←→ 我们的"**错误码藏在折叠卡里**"（A8-2）
- `IncrementalStreamingMarkdown.kt`(34,845) + `StableStreamingText.kt` + `StreamingMarkdownInteractionCommitGate.kt` ←→ 我们的"**中断后消息全空**"（A8-3，partial 不落 UI）
- `ToolResultContent.kt`(35,164) / `ToolPresentation.kt` / `ThinkingSegmentPresentation.kt` / `RetryActivityIndicator.kt`(7,907) / `SearchHighlighting.kt` / `Citation*` 三件 / `MessageBubbleAssets.kt`(38,007 vs 我们 6,803)
**处置**：M2 开工前先读上游 `GenerationErrorBar.kt` + `IncrementalStreamingMarkdown.kt`，**移植优先于自研**（MIT 允许，保留 LICENSE 即可）。
**附件不可见（A9-1）待判**：上游 `UserMessageBubble.kt` 17,099B ≠ 我们 15,102B，两边都改过 → 下一刀对照读这两个文件，定性是"我们弄丢"还是"上游原有"。

### 坑3 · 我们弄丢了上游的**代码体积门禁**（这条直接实现主人要的"缩减代码"）
`config/kotlin-source-size-baseline.txt`（185B，上游 2026-08-09 定的规矩）：
> **单文件上限 999 物理行**。超的登记为临时精确上限；**不许新增条目、不许抬高已登记数字**；文件回到 999 以下就删条目。
我们 `config/` 整个没带 → 门禁消失 → 于是有 `ProotSandboxManager.kt` **57,452B**（上游同期是 47,265B 且已拆）、`ChatApp.kt` 49.7KB、`SettingsManager.kt` 45.1KB…
**地基处置**：R10 直接改成"**继承上游 999 行门禁 + baseline 文件只减不增**"，不另发明。

### 坑4 · 隐私声明与实际不符（要诚实化）
我们的 `PRIVACY.md`（2026-05-29 版）写"**我们没有服务器**"，但：仓里留着 `server/crash/`（崩溃上报配套），且上游 README 明示还有**更新检查**与**评分(rating)网络请求**。上游还承认两个安全弱点，我们**不能盲目继承**：
- 密钥"legacy 值与加密失败时**故意回退明文**存 DataStore"
- `.agora` 导出包内的密钥**不加密**
**地基处置**：Hualuo 的隐私声明按**实际出口**逐条列域名；崩溃报告改自研适配 = **本地存一份 + 下次启动问一次才发**（沿用上游这套合规形状），且**默认指向我们自己的仓 issue，不留 newoether 的服务器**；密钥**禁止明文回退**，加密失败就拒绝保存并报错（R7）。
顺带：`remove_upstream_rating_form.py` 用"截断补右括号"删掉了上游**至今还在维护**的评分表单（上游 09-06 还修它 "neutral rating errors"）→ 说明**用手术刀删功能**必然和上游持续打架；Hualuo 直接不带该功能，源码里也不留残骸。

### 坑5 · 身份坑
包名仍是上游的 `com.newoether.agora`。同包名不同签名 = **装机时与原版互斥/更新冲突**，且分享 APK 时会被当成原版 → Hualuo 必须换 `com.hualuo.repotool`（已在 FOUNDATION §7 定为默认）。

## 三、主人 09-11 拍板（原话要点）
| 项 | 决定 |
|---|---|
| 本地模型 llama.cpp | **不用 → 不带**（子模块删） |
| Claude/GPT 导入器 | **没用过 → 不带**（保留 Agora 自备份 `.agora`/自有格式一条路） |
| proot 沙盒 | **保留**，按坑1 重做（provider 解析 + 成功复验 + 错误码） |
| 文档站 mkdocs | **不删，自研适配**：沿用上游两层结构 `docs/<locale>/`（面向使用者）+ `development/`（内部契约），但站只服务我们自己的仓 |
| fastlane | **删除** |
| 崩溃报告 | **不删，自研适配**（本地留存 + 确认后才发 + 指向自家 issue） |

## 四、策略建议（需要你拍一下）
既然原版活着、还在修我们用到的坑，**从零另起一仓 + 手工搬** 会把上游已修的坑重新踩一遍（沙盒就是现例）。建议：
1. **建正规网络 fork**：`newo-ether/Agora` → `xf8410/Agora`（GitHub 网络 fork，保留 `upstream` remote，之后一键同步上游修复，也就是 N2 的 fork 同步能力正好用得上）。
2. Hualuo 的**新代码只写我们自己的增量**（ProjectRegistry、投递/解压引擎、内置 GitHub、小黑板服务面），**基线 = 上游 master**，而不是 = 我们的 Workbench 屎山。
3. Workbench 里的自研工具（audit 五件/courier 三件/net_download/uma 观测族）**按迁移账逐件搬**，搬一件验一件（CI 绿）。
> 若你仍坚持"完全另起一仓从零写"，我就照 FOUNDATION §6 的 M0–M4 走，但**沙盒与流式渲染这两块必须先移植上游实现**，否则明知有解还重造 bug。

## 五、下一刀（我这边接着干，不等你）
1. 对照读 上游 vs 我们 的 `UserMessageBubble.kt`，定性附件不可见（A9-1），出修法。
2. 读上游 `GenerationErrorBar.kt`(5.5KB) + `AlpinePackageMetadataStore.kt`(5.8KB)，出"移植清单"。
3. 把 999 行门禁 + baseline 文件搬进 Hualuo CI（M0 的一部分）。
