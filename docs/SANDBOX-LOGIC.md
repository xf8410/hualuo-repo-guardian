# 沙盒逻辑深读（逐行扒，带字节偏移，不采信 commit message）

> **更正声明**：`docs/UPSTREAM.md` §坑1 里"我们缺 Alpine provider 解析层、上游 c880f379 是解"这个结论**作废**。
> 实读我们自己的 `app/src/fdroid/java/com/newoether/agora/sandbox/ProotSandboxManager.kt`（57,452B，sha256 `d68f6903…`，已落审计库）后发现：**provider 解析我们其实有**，是内联进这个大文件的——
> offset `26463` `parseFullApkIndex(indexFile)` → `repoPkgs` / `soToPkg`；`27717` `compareAlpineVersions(repoVer, instVer)`；`28058` 依赖走 `soToPkg[dn]?.let{}`。
> 上游那 5 个小文件（`AlpinePackageIndex.kt` 等）与我们的内联版本是**同一套逻辑的两种摆放**，不是"我们有他没有"。以下结论只用代码本身说话。
> 偏移单位=字节，均可 `audit_read_bytes`/raw 链接复核。

## 一、"python 秒报成功，然后环境错误，且没有错误码"——六个独立成因（全部实锤）

### D1 顶层包名**不走** provider 表，只有依赖走（口径不一致）
`26816`：`if (requested !in repoPkgs) { onProgress("FAIL: package '$requested' not found in index"); lastError = "Not found: $requested"; return false }`
→ 用户输入的 `python` 这类**别名/虚拟名**只在主包名表里查一次就直接判死；而依赖解析（`27997-28058`）却会 fallback 到 `soToPkg`。**同一份表，两套标准。**

### D2 `toInstall` 为空 = **立刻返回成功**（"马上成功"的直接来源）
`28255-28442`：
```
if (toInstall.isEmpty()) { addExplicitPackage(requested)
    onProgress("$requested is already installed and up to date."); return true }
```
而 `toInstall` 的填充判据只有 `27717`：`instVer == null || compare(repoVer, instVer) > 0`。
→ 只要"库里版本不比已装的新"（或 `compareAlpineVersions` 判成相等/更旧），**一个字节都不下，就报"已安装且是最新"**，顺手 `addExplicitPackage` 把它登记为显式包（写进 `/etc/apk/world`）。
→ 加上 `27533` `if (name in visited || name !in repoPkgs) return`：**索引里查不到的依赖被静默跳过**，所以"依赖齐了"这个判断本身是虚的。

### D3 **成功判据 = 包名出现在 installed DB**，故意不看 apk 退出码
`30772-30862` 注释原文：`Verify install — apk may return non-zero on minor post-install script errors` → `val installedOk = requested in readInstalledVersions()`；`34599`（apkDelete）更直白：`result.exitCode == 0 || removed`。
→ **post-install 脚本真炸了也算成功**（包名已入库）。python 这类包正是靠 post-install 建 `python3` 链接/site-packages 的——于是"装成功"和"`python3` 能跑"是两件事，我们只验了前一件。

### D4 错误通道**天生带不出码**
- `17801` `ProcessBuilder(args).redirectErrorStream(true)` → stderr 全并进 stdout，`stderr` 字段只剩 `""` 或 `"Timed out"`（`19376`/`19567`）；
- 失败时 `30925` `lastError = result.stderr.ifBlank { result.stdout }` → 实际要么空、要么整坨输出；
- 超时/异常统一 `-1` 哨兵：`19376` `if (ok) p.exitValue() else -1`、`19830` `catch(e:Throwable){ SandboxResult("", e.message ?: "proot failed", -1) }`、`20044` 未安装也 `-1`；
- `11752`/`12755`/`13628` 三个入口 `if (_isBusy.value) return` → **静默什么都不发生**（无提示、无日志）；
- `9250`/`12367` `catch (e: Throwable) { lastError = e.message }` → 只留 message，异常类型/退出码全丢。
→ 这四条合起来就是主人说的"提示环境错误或者下载失败，**没有其他错误码**"。

### D5 网络面：源和 DNS 全是硬编码，无换源无续传
`1609` `alpineMirror = "https://dl-cdn.alpinelinux.org/alpine/v3.21/main"`（索引 `25629`、每个 `.apk` `29031` 全用它，**唯一源**）；`25709-25885` 下载索引只判 `responseCode != 200`，无重试、无断点续传、进度用 `contentLength`；`8271` `etc/resolv.conf` 写死 `nameserver 8.8.8.8 / 1.1.1.1` → **国内 8.8.8.8 常不可达 = 域名解析不了 = "下载失败"**。rootfs 侧同样钉 `alpine-minirootfs-3.21.0-aarch64.tar.gz` + 单一 sha256（`1867`/`1998`，这两条是**做对的**：流式 64KiB + 校验和不符删档，`10802-11643`）。
`29804` 注释说明"走 Android HTTP 下载 + `apk add --no-network`"是为了绕开 VPN/Clash——方向对，但代价是 **apk 不再自己解依赖**，全部依赖我们自己算（于是 D1/D2 的错误会被放大成"少装东西"）。

### D6 环境面：解包器丢/复制软链 + `ensureShell` 用复制件顶包
`install()` 用 `extractTarEntries`（`7678-7895`；上游同名文件逻辑=目标不存在的软链 `continue` **直接丢弃**，存在的软链**把目标整棵复制成实体文件**，`catch(_:Throwable){}` 静默）→ Alpine rootfs 里大量 `/bin→/usr/bin`、`/usr/bin/python3→python3.12` 这类链接变成**过期副本**；
`5435-5898` `ensureShell()` 发现 `/bin/sh` 不存在时，把 `busybox` **复制**成 `/bin/sh`（`busybox.copyTo(sh,false)`）来"救活"环境；`12016`/`13479`/`13892` 每次装包/删包/升级后再调一次 `ensureShell()` 兜底。
→ 一旦 apk 之后替换了 busybox，`/bin/sh` 就是**没人维护的旧副本**：命令"能起来"但行为错，报出的就是"环境错误"这一类没有任何码的失败。
另有 `4211-4477`：`System.loadLibrary("agora_proot")` 与真正使用的 `libproot_exec.so` 名字不一致，且 `catch(_:Throwable){}` 吞掉后**照旧拼路径继续**；`16490` `ensureTalloc()` 靠复制 `libtalloc.so`→`libtalloc.so.2` 绕过 Android 链接器按文件名找 SONAME 的问题。

## 二、同文件顺手扒出的其它逻辑缺陷（一并进地基修复清单）
| # | 位置 | 问题 |
|---|---|---|
| L1 | `20912` | `f.inputStream().use { it.skip(s); it.read(buf) }` —— **单次 `read()` 不保证读满**，带 offset 的 fileRead 会短读/返回残缺内容（应 `readNBytes`） |
| L2 | `22835` | `fileGrep` 对每个文件 `readText()` **整文件入内存**（只挡 >500KB），且结果条数**无上限**、递归无深度默认（`22351`）→ 大目录= OOM/卡死老路 |
| L3 | `24078` | `fileEdit` 用 `content.split(oldString).size - 1` 计数：`oldString=""` 时 Kotlin 按字符切 → 假"找到 N 处"；且 readText/ writeText 全量读写，无并发保护外的原子替换 |
| L4 | `31192-32486` | `apkList` 每次 `readText` 整个 installed DB 并全量解析成 UI 列表（无缓存无分页），失败只往 `_terminalOutput` 塞一行 `[apkList: …]` 然后返回 `emptyList()` → **UI 上表现为"包列表空了"而不是"出错了"** |
| L5 | `25443`/`32988`/`34823` | `ensurePackageMetadata()` 隐式 `captureBaseWorld()`：首次快照发生在**用户已经装过东西之后**，于是把用户包冻结成"base" → `33410-33487` 之后拒绝 `apk del`（"Refusing to remove base package"）——**我们和上游共有这个顺序坑**（上游 `AlpinePackageMetadataStore.kt` `readBaseWorld()` 里同样懒调 `captureBaseWorld()`） |
| L6 | `24868`/`30103`/`30483` | `apk add --allow-untrusted` —— 绕开签名校验（配合 D5 的 `--no-network`）；我们自己下 `.apk` 但**不校验每个 `.apk` 的 sha256**（rootfs 反而校验了）→ 下载被劫持=装进脏包 |
| L7 | `28597-29631` | 下载缓存文件名 `"$name-$ver.apk"` 落在 `filesDir` 根目录（不清理旧版本），装完只清 `rootfs/tmp`；`f.copyTo(dst,true)` 双份占用空间 |

## 三、地基修复方案（对应编号，v0 必做）
1. **包名解析统一**：`requested` 与依赖走**同一套** resolve（先主名 → 再 provides/`soToPkg` 别名 → 再版本约束剥离），并把"解析成了哪个真包名"**回显给用户**（`python` → `python3`）。
2. **成功判据换成三条一起看**：`exitCode == 0` **且** 包在 installed DB **且** 期望的可执行入口 `command -v <bin>` 存在（或 `apk info -e` 复验）。任一不满足 → 失败。
3. **禁止假成功路径**：`toInstall.isEmpty()` 必须先 `apk info <pkg>` 证实"确实装着且版本对"才回"已最新"，否则继续安装；不再顺手 `addExplicitPackage`。
4. **错误对象化**（对应 R7）：`SandboxError(kind, exitCode, stderrSnippet, pkg, url)` 五字段；`-1` 不再当哨兵；`installPackage` 等在 `_isBusy` 时**必须**回一条"上一个操作还在跑"，不许静默 return。
5. **源与 DNS 可配 + 换源**：`repositories`/`resolv.conf`/mirror 全进设置；失败自动切镜像列表（清华/中科大/官方）；下载带 **If-Range 断点续传 + 每个 .apk 的 sha256 校验**（对齐 D5/L6）。
6. **解包器重写（不许再复制软链）**：真 `Os.symlink` 建链接（Android 私有目录支持），dangling 链接照建不丢；`ensureShell()` 降级为"诊断报告"而不是偷偷复制二进制顶包。
7. **base-world 快照只在 `install()` 成功那一刻 force 写**（`9135` 已经 force），并把 `readBaseWorld()` 的懒 `captureBaseWorld()` 去掉（治 L5，上游同坑）。
8. L1 用 `readNBytes`；L2 流式逐行 + 命中上限 + 深度默认；L3 空串/超长明确拒绝；L4 `apkList` 失败必须冒错。

## 四、还没下结论的两件（诚实标注）
- **上游是不是更对**：上游 `ProotSandboxManager.kt` 是 47,265B 的另一种摆法，`apkInstall` 的判据我没读，**不猜**。要读再开两个窗口。
- **A9-1 附件不可见**：上游 `UserMessageBubble.kt` 17,099B vs 我们 15,102B，两边都动过；只看了我们的可见性判据（`metaOnlyItems` 白名单 filter），**DB 往返是否丢 `attachmentMeta` 未验**。
