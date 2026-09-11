# Hualuo 地基章程 v0

> 本仓库 = **Hualuo全自动仓库工具** = Agora-Workbench 的地基重写后代（**仓库自动化/研发工作台**）。
> 边界铁律：Hualuo **服务**小黑板那条线（采集/协议/浮窗/决策），但**本身不是小黑板**，不装游戏玩法。
> 本文件取代 Agora 的 54KB `ARCHITECTURE.md`。上限 8KB，超了先删再说。

## 1. 存量债实查账（2026-09-11，全部本轮实查，非记忆）

### 1.1 血统与 py 补丁（主人点查的两项，坐实）
- 包名 `com.newoether.agora` = **克隆他人后魔改**，未改身份；`scripts/remove_upstream_rating_form.py` 即"删上游评分表单"的字符串手术。
- `scripts/` 现存 **17 个 py**，其中 12 个是 `apply_*_fix.py` 型 **源码字符串手术刀**：`read_text()` → 匹配 `old` 块 → `write_text(replace)`，锚点靠硬编码空白/缩进（见 `apply_reply_disappears_fix.py` 8 行整块字面匹配）。锚点一处对不上就 `raise`/静默不生效。
- `scripts/finalize_main_integration.py` 曾删 8 个 apply 脚本 + 4 个一次性 workflow + `ci-failure-summary.txt`，并重写 build-workbench.yml；但 **5 个手术刀仍留在仓**：`apply_chat_history_resilience_fix.py`(9.4KB)、`apply_chat_performance_fix.py`(12.9KB)、`apply_copy_action_visibility.py`、`apply_uma_workbench_integration.py`、`remove_upstream_rating_form.py` → **是否已应用到源码 = 未核对**，属"改了什么没人知道"一类。
- 那把手术刀里写的守卫 **"Reject self-mutating workflow commands"（grep 禁 git push/gh api contents）在现版 `build-workbench.yml` 中已消失** = 守卫被后续改动覆盖回退（回归实锤，A4 同类）。
- **编译错误：本轮无活口**。main `dd6e7d6f` 构建 **success**（run 34540078345，09-10 23:09Z）；`workbench/sqlite-oversize-row-guard` success（34595349522）；`workbench/fgs-start-timeout-60` success（34587334249）。
- 但 CI **把编译错误藏起来了**：`build-workbench.yml` 三个关键步骤全 `continue-on-error: true` + 输出重定向到文件后只 `tail -n 200` + 末尾才 `Enforce CI result`。后果：①早期编译错误被 tail 截断，UI 里看不见；②红被伪装成 skipped；③靠最后一步兜底=事后补救，不是门禁。这正是主人怀疑的"用办法绕过去了"。

### 1.2 巨型文件榜（瘦身靶子，实测字节）
| 文件 | 大小 |
|---|---|
| `ARCHITECTURE.md` | 54.4KB |
| `ui/chat/ChatApp.kt` | 49.7KB |
| `data/SettingsManager.kt` | 45.1KB |
| `MainActivity.kt` | 44.6KB |
| `ui/chat/message/AssistantMessageContent.kt` | 43.7KB |
| `data/DataImporter.kt` | 39.7KB |
| `ui/chat/bottombar/ChatBottomBar.kt` | 37.2KB |
| `data/local/ChatDatabase.kt` | 32.1KB |
| `data/repository/SettingsRepository.kt` | 31.4KB |
| `ui/chat/message/MessageItemTimeline.kt` | 29.2KB |
| `ui/chat/message/SegmentDetailSheet.kt` | 28.5KB |
| `data/DataExporter.kt` / `GptChatImporter.kt` / `ui/chat/AgentTeamDialog.kt` / `ChatDrawerContent.kt` | 23.0 / 20.0 / 22.8 / 20.4KB |
| GenerationManager / ProotSandboxManager | ~57KB 各（**记忆值，待复验**） |

> 单文件 >20KB 就是拆分信号；Agora 有 **20+ 个** 这样的文件 = 典型的"补丁摞补丁"堆积。

### 1.3 夹带物（克隆残留/一次性产物，v0 默认不带）
`apk-probe/` `server/` `fastlane/` `assets/` `build-proot.sh`(9.5KB) `ci-failure-summary.txt`(211B) `probe-root.txt`(26B) `mkdocs.yml`(16KB) `.gitmodules`(llama.cpp + termux/proot，thirdparty 内还有 `talloc/` 真目录) —— **submodule 每次 CI `submodules: recursive` 拉全量 = 时间与磁盘的黑洞**。

## 2. 地基红线（R1–R11，CI 可判定的写成门禁）

| # | 红线 | 门禁 |
|---|---|---|
| R1 | **禁"构建时生成/修改源码"**（py/heredoc/sed 一律不许写 .kt/.kts；补丁只能由人和 PR 产生） | CI grep：`.github/workflows/**` 出现 `python.*>.*\.kt`/`sed -i`/`git push` → 红 |
| R2 | 流式 IO 铁律：禁 `readAllBytes`/整文件入内存，64KiB 窗口贯穿上传/哈希/解压 | CI grep 引擎模块 `readAllBytes\|readBytes()` → 红 |
| R3 | 前台服务三件套模板：`startForeground()` 首行 + 兜底 + 3.5s 看门狗 + fail-closed；启停统一"前台才动服务"；禁裸 `startService`（A5 病） | 模板类 + 单测 |
| R4 | 版本单源 `version.properties`，构建脚本与守卫测试都从它读 | CI 断言三处一致 |
| R5 | 消息库禁无界 TEXT：大 payload 落盘、DB 只存引用；启动期 repair 走 SQL 侧（根治 A1 `SQLiteBlobTooBigException` 变砖） | 单测 + 列类型守卫 |
| R6 | 新增 `object/class` 必须被引用；工具注册与回归清单同变更内改（根治 A1 死代码 + 漏登） | 编译期 `-Werror` + 注册表测试 |
| R7 | 错误必须可见：`ToolResult(isError, errorKind)` 类型化，气泡顶部红徽标 + 振动，不许埋在折叠卡里（A8-2） | UI 快照/单测 |
| R8 | 永不空气泡：流式草稿按 N token checkpoint，停止/524/错误都把 partial 提交为"已中断"消息（含原因/错误码/token）（A8-3） | 单测（cancel/error 两路径） |
| R9 | CI 不 `tail` 截错：步骤**不得** `continue-on-error`，失败原文进 artifact + 摘要首屏贴前 40 行错误（治 1.1 藏错误） | workflow lint |
| R10 | 单文件 >20KB = 拆分信号；新文件 >8KB 需在 PR 说明理由 | CI `wc -l` 门禁（存量白名单只减不增） |
| R11 | 大二进制/大目录**永不进聊天上下文**：只按"路径+行号"或端点分块读，单块 ≤10KB（治 4MB→乱码→524 洪水） | 会话侧纪律 |

**Kotlin 写法改进（主人点题）**：`com.hualuo.repotool` 全新身份；`data class + 具名参数`代替字符串手术；`sealed interface` 表达附件类型（现在靠 `type: String` 魔法串 `"image"/"video"/"file"/"pdf"`，拼错即静默）；`StateFlow` + 150ms 节流单一收集器（代替刷屏刷主线程）；纯函数引擎模块（`engine/` 无 Android 依赖，可本地/JVM 单测）；`Result`/明确异常代替 `runCatching{}` 吞错；`@Composable` 单元 ≤120 行；禁 `!!`。

## 3. 新 bug 登记：附件发出后聊天框看不见文件（主人 09-11 报）

**症状**：发**非图片**附件后气泡里看不到它；图片正常。
**已定位到的证据链**（本轮实查）：
1. `model/AttachmentMeta.kt`：附件是 `AttachmentItem(type/file_name/mime_type/image_index/page_count/text_content...)`，**非图文件常常没有 `imageIndex`**，靠 `images` 之外的"meta-only 项"渲染。
2. `ui/chat/message/UserMessageBubble.kt` 的可见性判据：`hasMetaItems = attachmentMeta?.items?.isNotEmpty()`；`metaOnlyItems = items.filter { it.imageIndex == null && type in (file|pdf|image) }` → **只要 `attachmentMeta` 在送进气泡的那条消息上是 null/空，或某 type 不在白名单（如 `video` 无 imageIndex、后续新格式），文件卡片就静默消失**，而图片走 `message.images` 所以不受影响 —— 与症状吻合。
3. `scripts/apply_reply_disappears_fix.py` 证明：`userMessage(... attachmentMeta = attachmentMeta)` 这段**曾是靠 py 补丁事后插进 `MessageGenerationController.kt` 的**，且 `remember(message.images, meta)` 的重算键只认这两个对象 —— 属于"发出去那一刻没带 meta"与"DB 往返丢 meta"两条路之一。
**地基首刀（v0 第 1 个 PR 内做完）**：分块读 `data/repository/ConversationRepository.kt` + `data/local/ChatDatabase.kt`，确认 `attachmentMeta` 是否持久化+回读；再把可见性判据从"白名单 filter"改成**穷举 sealed type 的 when（编译期不允许漏分支）**，任何未识别附件必须渲染成"未知文件"卡片而不是不渲染。
**验收**：①发送即见 ②杀 app 重开仍在 ③非图文件有卡片+文件名+大小 ④点开可读 ⑤不认识的格式也必须出现（不允许静默丢弃）⑥附件写库失败必须红徽标（R7），不得空气泡（R8）。

## 4. 新需求进地基（不再另开洞）

- **N1 项目关联记忆库 ProjectRegistry**：内置项目地图（owner/repo/角色/与其他项目的关系/权限=我的|他人只读|上游/最后实查时间/常用分支），会话与 UI 都能直接引用，**主人不必再发网址**。Agora 现有 `data/FavoriteSitesStore.kt`(8.3KB) 是雏形但只有链接；Hualuo 做成结构化 + 可被工具检索（`project_lookup`）。数据源=本仓 `projects.toml`，跟 `agora-data-backup` 的 `常用网站.md` 对账。
- **N2 内置 GitHub app + fork 同步**：仓库浏览/文件读/搜索/PR/CI 面板 + **一键 upstream→fork 同步**（fast-forward，落后/分叉/reset 需二次确认），把"我这边每次手工搬"变成 app 内动作。fork 关系（如 `xf8410/umaai-rs` ← `xulai1001/umaai-rs`）写进 ProjectRegistry，同步时按表操作。
- **N3 上传全格式**：任何格式可选上传，流式分卷 + 真实进度条 + 断点续传，**大文件永不闪退**（R2）。
- **N4 给密码即可解压**：zip4j AES / 7z 密码 / tar.gz 链式 + **魔数嗅探不认扩展名** + `ExtractGuard`（zip-slip / 炸弹四道上限）。
- N5 UI 三保证（A8）：弹层强制不透明 Surface 禁手调 z 序、错误徽标、partial checkpoint。

## 5. 瘦身预算（功能不减、bug 不增）
1. **直删**（零功能损失）：17 个 py 手术刀（已应用的=历史，未应用的=必须人工改源码）、`ci-failure-summary.txt`、`probe-root.txt`、54KB `ARCHITECTURE.md`（本文件取代）、`mkdocs.yml`、一次性 workflow。
2. **不随迁**（另线自养）：`apk-probe/`（属最佳球会逆向线，留 Agora 或独立仓）、`server/crash`、`fastlane`、`build-proot.sh` + 三个 submodule（llama.cpp/proot/talloc）—— **待主人确认哪些功能真在用**（见 §7）。
3. **合并下沉**：消息渲染族（`MessageItem*` 6 文件 87KB）→ 一个 `message/` 小族 + 纯函数段落模型；设置族（`SettingsManager` 45KB + `SettingsRepository` 31KB + `SettingsAboutPage`）→ 单一 typed prefs 层；导入族（`DataImporter` 40KB + `Gpt` 20KB + `Claude` 13.7KB）→ 一个 codec + 各家 200 行薄适配器。
4. **预算**：app 侧 Kotlin 目标 ≤ Agora 的 **40%**（本轮先量出 Agora 分母，v0 立 CI 计数门禁，只减不增）。

## 6. v0 里程碑
| M | 内容 | 门禁 |
|---|---|---|
| M0 | 仓库骨架：`engine/`(纯 JVM) + `app/`(Compose) + `version.properties` + CI(R1/R2/R4/R9/R10 门禁) + ProjectRegistry 表 | 空跑 CI 绿、能出 APK |
| M1 | 传输引擎：全格式流式上传 + 进度 + 断点续传 + 分卷投递；解压 + 密码 + ExtractGuard | 单测（含 0 字节/超密/炸弹/slip） |
| M2 | 会话与附件：DB 引用式存储(R5) + 附件穷举渲染(§3) + 错误徽标(R7) + partial checkpoint(R8) | 单测 + 装机验收 |
| M3 | GitHub 内置：ProjectRegistry + 浏览/PR/CI + fork 同步(N2) | 端到端一次真同步 |
| M4 | 工作台工具族迁移账（按 `agora-workbench-tool-roadmap.md` §一逐条搬，**不凭印象重推**） | 工具注册表回归锁 |

## 7. 待主人拍板（不回就按默认走）
1. **Rust**：Agora 根树里没有 Rust 模块（gradle/thirdparty/scripts 而已）→ 默认 **Hualuo 纯 Kotlin/JVM**，Rust 只作为"调用现成 SO"（hlpatch 侧另账）。
2. 包名/ID 默认 `com.hualuo.repotool`，显示名「Hualuo全自动仓库工具」，仓名 `xf8410/hualuo-repo-tool`（已建）。
3. **功能取舍**（决定能砍多少）：本地模型(llama.cpp submodule)、proot 沙盒、Claude/GPT 导入器、fastlane、`server/` —— 哪些你还在用？不用的我地基里**直接不带**。
4. v0 范围默认 **M0–M2**（地基+传输+附件），工具族迁移放 M4。
