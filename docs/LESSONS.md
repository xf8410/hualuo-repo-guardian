# AI 踩过的坑（LESSONS）

> 这份文档记录** AI 在多个仓库上犯过的具体错**——目的是让 hualuo-repo-guardian 的守门机制能针对性地防住这些错。
> 每条坑都附"对应的红线编号"——本仓库哪条红线是专门挡它的。

---

## 坑 1：CI "假绿"（外绿内红）

### 现象

GitHub Actions run 整体显示绿勾（success），但 run 内部某步实际失败了。原因：

1. **`continue-on-error: true`**——step 失败不算红，run 仍绿
2. **`tail -n 200` 截断日志**——错误行在 tail 之后被截掉
3. **`|| true`** ——`make build || true` 把失败吞掉
4. **`set +e`** ——禁用错误检查
5. **`if: always()` 隐藏关键 step 失败**——失败摘要没暴露

### 真实案例

- **Agora-Workbench** 的 `build-workbench.yml` 三步全开 `continue-on-error:true`，编译失败被伪装成"跳过"，CI 表面绿、内部红
- 旧版 CI 把所有日志 `tail -n 200`，编译错误在 tail 之后——run 看起来绿，错误其实在那里
- 旧版 CI 用 `bash -e`，把失败轮里的日志查看命令全变成死代码——想查都查不了

### 防御

→ **红线五·禁止假绿机制**（HARD-GATES.md 红线五）

---

## 坑 2：一次性塞多个功能

### 现象

一个 PR / 一个 release tag 塞了多个不相关功能——A feature + B 重构 + C bug 修 + D typo 修。后果：

1. **回退困难**——一个 feature 有问题，整个 release 都得回退
2. **审计失效**——diff 跨多 module，没法独立验证
3. **CI 难红**——一个 feature 编译失败连带把整个 release 都废掉
4. **责任不清**——出问题时不知道哪个 feature 引入

### 真实案例

- Agora 多次"全面升级" PR 一次塞 10+ 不相关改动，最后只能 force-push 回滚
- hualuo-repo-tool workbench 分支出现过把"CI 改动 + engine 改动 + app 改动"塞同一个 PR，CI 红了分不清是哪个模块

### 防御

→ **红线六·PR 必须单功能**（HARD-GATES.md 红线六）
→ **红线八·每 tag 单次 CI + 单 feature release**（HARD-GATES.md 红线八）

---

## 坑 3：artifact 嵌 zip（16.5 MB 自检日志）

### 现象

CI 产出的自检日志 artifact 大到 16.5 MB，里面还嵌了一堆 zip-of-zip。后果：

1. **配额撑爆**——GitHub Actions artifact 免费配额 500 MB，几次就撑爆
2. **下载慢**——16.5 MB 里大部分是冗余的嵌套 zip
3. **审计困难**——zip-of-zip 没法直接 grep，必须一层层解压
4. **CI 红但被掩盖**——artifact 上传失败被吞，run 仍绿

### 真实案例

- hualuo-repo-tool 自检 #317 artifact 16.5 MB（截图证据），里面估计塞了 build 产物 + 多层 zip
- #301 配额撑爆到 870 MB，CI 直接挂

### 防御

→ **红线七·artifact 不嵌 zip**（HARD-GATES.md 红线七）

---

## 坑 4：版本号三处不一致

### 现象

版本号写在多处（`Cargo.toml` / `lib.rs` / GUI 标签）——改一处忘了另一处，结果：

1. **编译产物版本与发布版本不一致**——用户装到手机上是 v3.7.1，仓库声明 v3.7.0
2. **CI 假绿**——CI 检查 `Cargo.toml` 通过但实际编译时用了 `lib.rs` 的版本
3. **回退困难**——不知道哪个版本号是真

### 真实案例

- hlpatch-lite 三处版本号（`Cargo.toml` / `lib.rs` / `lib.rs` 的 INFO 输出）写不一致的历史
- hualuo-repo-tool 已经解决：`version.properties` 是唯一来源，CI 核对 `app:printVersion` 输出是否一致

### 防御

→ **红线四·版本号只能有一个来源**（HARD-GATES.md 红线四）

---

## 坑 5：force push 过头

### 现象

`git push --force` 覆盖了重要 commit，回滚后 git log 不再有历史：

1. **审计失效**——git log 缺一段历史，谁也不知道删了什么
2. **合作困难**——其他 fork 的 PR 无法 rebase
3. **责任不清**——删的 commit 谁都看不到

### 真实案例

- hlpatch-lite force push 回滚过头，连 CI 配置都没了
- 改 force push 后用 `git reset --hard HEAD~1` 错对象（HEAD 变了）

### 防御

- **main 分支禁止 force push**——branch protection 设置
- 必须 `git push --force-with-lease`（不是 `--force`）——如果远端有新 commit，强制失败
- 红线五检测 .github/workflows/ 里有没有禁用强制 push 的钩子

---

## 坑 6：sed/awk 改源码污染文件

### 现象

用 `sed -i 's/foo/bar/' file.kt` 改代码——结果回显写入了文件、特殊字符没转义、文件破坏：

```bash
# 实际结果：file.kt 里多了 sed 的回显
sed -i 's/foo/bar/' file.kt
sed: -e expression #1, char 9: unterminated `s' command
# file.kt 现在内容是：
#   原内容...
#   sed: -e expression #1, char 9: unterminated `s' command
```

### 真实案例

- hlpatch-lite 多个 sed/awk 操作污染了 lib.rs

### 防御

→ **红线一·禁止构建时改源码**（HARD-GATES.md 红线一）

---

## 坑 7：CI step 命名用中文键

### 现象

GitHub Actions 的 `jobs:` 下面的**键名**用中文：

```yaml
# ❌ 红——GitHub 解析失败
jobs:
  自检:    # ← 中文键名
    name: 编译 + 测试 + 打包 + 四条红线
```

后果：零 job、日志全空、run 名字退化成文件路径。中文只能写在 `name:` 里。

### 真实案例

- hualuo-repo-tool ci.yml 注释里专门记了这条坑

### 防御

- 写红线检查：workflows yml 里 `jobs:` 下不许出现中文键名
- 中文必须写在 `name:` 字段

---

## 坑 8：xargs wc -l 多文件出错

### 现象

```bash
find . -name '*.kt' | xargs wc -l
# 输出最后一行：1000 total
```

多文件时 wc 额外打一行 `total`——"全仓加起来刚过 1000 行"被当成"某个文件超了 999 行"——报的还是 `1000 total` 这种连文件名都没有的行。

### 真实案例

- hualuo-repo-tool ci.yml 红线三里专门记了这个坑

### 防御

- 红线三改用逐文件 `wc -l < 文件`

---

## 坑 9：依赖 actions/setup-android 装环境

### 现象

`android-actions/setup-android` 在 run 34903710256 起自己崩（`Failed to find package 'tools'`），构建一行没跑就红。

### 真实案例

- hualuo-repo-tool ci.yml 注释里专门记了

### 防御

- 不依赖它——`ubuntu-latest` 本来就带 SDK（ANDROID_HOME 已设），缺的组件让 AGP 自己下
- 这一步只做"环境在不在"的断言，装不到就报得清清楚楚

---

## 坑 10：本地模型/CLi 引入脏依赖

### 现象

Agora 引入过 llama.cpp + proot + talloc 等巨型依赖，最后仓库膨胀到几十 MB。

### 真实案例

- Agora-Workbench audit 报告里点名了"thirdparty(llama.cpp+proot+talloc)" 巨型文件
- hualuo-repo-tool D-04 决策："本地模型 llama.cpp **不带**，子模块删"

### 防御

- 引入第三方依赖前必查：D-04 / D-05 / D-06 / D-07 决策账
- 巨型依赖（>1MB）必须走 D-决策账评审

---

## 坑 11：CI 不挂日志上传 step

### 现象

CI 失败时日志被截断 / 不上传——事后没法查：

```yaml
# ❌ 红——失败时日志可能上传失败，run 仍绿
- name: 收日志
  uses: actions/upload-artifact@v4
  if: always()
  with:
    name: log
    path: logs/
```

### 真实案例

- 旧 Agora 配额满时日志上传失败，run 仍绿，事后查不到失败现场

### 防御

- 红线五明确：`upload-artifact@v4` 的失败可以用 `continue-on-error`，但必须在同一个 step 的 `if: always()` 块里打印配额状态
- artifact 上限：自检日志 5 MB，避免配额撑爆

---

## 坑 12：CI 用 python 重写源码再编译

### 现象

CI 在编译前用 python 改源码：

```yaml
# ❌ 红——产物与仓库内容对不上，谁也没法审计
- run: python3 patch.py
- run: gradle build
```

后果：

1. **审计失效**——git diff 看的是 patch 前的代码，CI 编译的是 patch 后的代码
2. **责任不清**——bug 是源码的 bug 还是 patch 的 bug？
3. **重放困难**——别人本地 clone 复现不了 CI 的产物

### 真实案例

- 旧 Agora 的构建会先用 python 重写源码，产物与仓库内容对不上

### 防御

→ **红线一·禁止构建时改源码**（HARD-GATES.md 红线一）

---

## 守门总账

| 坑 | 防线 |
|---|---|
| 坑 1 CI 假绿 | 红线五 |
| 坑 2 一次性塞多 feature | 红线六 + 红线八 |
| 坑 3 artifact 嵌 zip | 红线七 |
| 坑 4 版本号三处不一致 | 红线四 |
| 坑 5 force push 过头 | branch protection + `--force-with-lease` |
| 坑 6 sed 污染源码 | 红线一 |
| 坑 7 CI 中文键名 | 红线五检查脚本 |
| 坑 8 xargs wc -l | 红线三逐文件统计 |
| 坑 9 setup-android 依赖 | 显式断言 |
| 坑 10 巨型依赖 | D-决策账 |
| 坑 11 CI 不挂日志上传 | 红线五 + 红线七 |
| 坑 12 CI 用 python 改源码 | 红线一 |
