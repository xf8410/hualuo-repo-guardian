package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.model.SubPage

// 设置页二级字段表：照抄 ui/v13.html 的 SUB 表（原型逐行翻译，演示数据）。
// 真设置项（写进设置文件的那种）在 SettingsCatalog.kt，别混到这里。
//
// 家规：图标只写 IconKey 键名，图形字符不进源码（闸门 NoEmojiInSourceTest 连注释一起扫，
// 所以下面提到旧写法时用「对勾」「警告三角」「箭头」这些词，不贴字符本身）。
// 原文案里三处用符号当语气的写法改成了文字：
//   - 「工具对勾 视觉对勾」改为「支持工具与视觉」；
//   - 「裁剪时界面出声（警告三角 行）」改为「（警告行）」；
//   - 接力与观测说明里的箭头改为顿号与逗号。
// 意思不变，只是不再拿字符当图形用。

val SubPages: Map<String, SubPage> = mapOf(
    "provider" to SubPage("提供商", listOf(
        SubField.Row("bai2 网关", "OpenAI 兼容 · 已配置 ›"),
        SubField.Row("官方 API", "OpenAI / Anthropic · 已配置 ›"),
        SubField.Row("本地 ollama", "127.0.0.1:11434 · 在线 ›"),
        SubField.Row("＋ 添加提供商", "自定义 OpenAI 兼容端点"),
        SubField.Row("Claude 配置导入", "从官方 Claude 应用搬家 ›", gotoKey = "claudeimport"),
        SubField.Note("密钥只存本机，明文（拍板 D-10）；导出备份会带上密钥，别外传"),
    )),
    "claudeimport" to SubPage("Claude 配置导入", listOf(
        SubField.Row("检测到的 Claude 配置", "~/.claude · 1 份 ›"),
        SubField.Switch("导入对话历史", true),
        SubField.Switch("导入项目说明（CLAUDE.md）", true),
        SubField.Seg("重名会话", listOf("跳过", "改名后导入"), 1),
        SubField.Button("开始导入"),
        SubField.Note("一次性搬家：导入后就是普通会话；密钥不搬，需要重新填"),
    )),
    "model" to SubPage("模型", listOf(
        SubField.Switch("qwen3.8-flash · 1M · 支持工具与视觉", true),
        SubField.Switch("qwen3-max · 256k · 支持工具", true),
        SubField.Switch("gpt-4o-mini · 128k", true),
        SubField.Switch("claude-sonnet · 200k", true),
        SubField.Switch("qwen2.5-coder:32b · 32k · 本地", true),
        SubField.Input("别名（聊天框胶囊显示名）", "留空=模型原名"),
        SubField.Note("上下文上限从模型端探测，探测不到就标灰「未知」，不许手填冒充"),
    )),
    "proxy" to SubPage("代理", listOf(
        SubField.Seg("类型", listOf("无", "HTTP", "SOCKS"), 0),
        SubField.Input("地址", "127.0.0.1"),
        SubField.Input("端口", "7890"),
        SubField.Note("只影响 AI 请求和 GitHub API，不影响游戏观测桥"),
    )),
    "prompt" to SubPage("系统指令", listOf(
        SubField.Row("家规 · 全局", "结论必须带证据；丢东西要出声 ›"),
        SubField.Row("仓库工 · 会话级", "改动走 workbench 分支，CI 绿了才提 PR ›"),
        SubField.Row("＋ 新建指令"),
        SubField.Note("全局指令每条对话都带；会话级只在指定对话生效"),
    )),
    "gen" to SubPage("生成参数", listOf(
        SubField.Slider("温度", 0.0, 2.0, 0.7, 0.1),
        SubField.Slider("top_p", 0.0, 1.0, 0.95, 0.05),
        SubField.Seg("带多少历史", listOf("20 条", "50 条", "按 token 装满"), 2),
        SubField.Note("历史「按条数」会浪费大上下文模型，默认按 token 装到真值上限"),
    )),
    "title" to SubPage("标题生成", listOf(
        SubField.Switch("启用", true),
        SubField.Seg("用哪个模型", listOf("跟随当前", "固定小模型（省钱）"), 1),
        SubField.Input("起标题的提示词（可空）", "10 字以内，概括主题"),
    )),
    "trim" to SubPage("历史裁剪", listOf(
        SubField.Seg("策略", listOf("按 token", "按条数"), 0),
        SubField.Input("单回合上限 token", "128000"),
        SubField.Switch("裁剪时界面出声（警告行）", true),
        SubField.Note("这条开关默认锁死，关掉等于允许静默丢历史——红线"),
    )),
    "caption" to SubPage("图像转述", listOf(
        SubField.Seg("转述用模型", listOf("qwen3.8-flash", "gpt-4o-mini"), 0),
        SubField.Input("转述要求", "说清图里的文字、数字、界面布局"),
        SubField.Note("目标模型没有视觉时自动先转述；有视觉直接原图发"),
    )),
    "transcription" to SubPage("语音转写", listOf(
        SubField.Row("转写模型", "whisper-large-v3 · OpenAI ›"),
        SubField.Row("已启用模型", "whisper-large-v3（OpenAI）· paraformer（本地）›"),
        SubField.Row("＋ 添加模型", "从提供商拉取可用列表 ›"),
        SubField.Input("高级 · 转写提示词", "例：中文对话，必须带标点"),
        SubField.Note("语音消息先转文字再发给模型；本地模型首次下载后离线可用"),
    )),
    "imagegen" to SubPage("图像生成", listOf(
        SubField.Seg("服务商", listOf("OpenAI", "ComfyUI 本地", "Pollinations 免费"), 0),
        SubField.Seg("尺寸", listOf("1:1", "16:9", "9:16"), 0),
        SubField.Switch("生成结果自动作为附件登记", true),
    )),
    "websearch" to SubPage("网页搜索", listOf(
        SubField.Radio("选择搜索提供商", WebSearchProviders, DefaultSearchProviderId),
        SubField.Input("API 密钥（Brave/Serper/Tavily 需要）"),
        SubField.Note("沿用本应用内置的五家搜索提供商（移植不重写）；DuckDuckGo 免密钥但可能触发反爬，SearXNG 建议自建实例"),
    )),
    "chatsearch" to SubPage("对话搜索", listOf(
        SubField.Switch("启用", true),
        SubField.Seg("方式", listOf("关键词（快）", "语义（要嵌入模型）"), 0),
        SubField.Seg("嵌入模型", listOf("OpenAI", "ollama 本地", "不下载"), 2),
        SubField.Note("语义搜索首次要下嵌入模型（约 80MB），下完离线可用"),
    )),
    "shell" to SubPage("终端", listOf(
        SubField.Switch("启用", false),
        SubField.Seg("后端", listOf("proot 沙盒", "禁用"), 0),
        SubField.Slider("单命令超时", 10.0, 300.0, 60.0, 5.0),
        SubField.Row("沙盒详情", "Alpine mini · 已安装 ›", gotoKey = "sandbox"),
        SubField.Note("命令在沙盒里跑；写操作和危险命令执行前弹确认"),
    )),
    "sandbox" to SubPage("沙盒", listOf(
        SubField.Row("根文件系统", "Alpine mini · 84 MB · 已安装 ›"),
        SubField.Row("共享目录", "Download/hualuo-sandbox/ ›"),
        SubField.Switch("允许沙盒联网", false),
        SubField.Slider("内存上限 MB", 128.0, 2048.0, 512.0, 64.0),
        SubField.Note("沙盒与系统隔离：写操作只落在根文件系统里，退出可选清空；共享目录是唯一互通口"),
    )),
    "github" to SubPage("GitHub 工作台", listOf(
        SubField.Row("登录状态", "xf8410 · 令牌有效 ›"),
        SubField.Input("默认仓库", "xf8410/hualuo-repo-tool"),
        SubField.Switch("记忆文件同步到私有仓", true),
        SubField.Row("Actions 监视", "1 个 run 在盯 ›", gotoKey = "ghactions"),
        SubField.Note("写操作只进 workbench/* 分支，main 靠 PR 合"),
    )),
    "ghactions" to SubPage("Actions 监视", listOf(
        SubField.Row("run 34723037840", "success · build-workbench ›"),
        SubField.Row("run 34742845079", "success · bench ›"),
        SubField.Switch("只在失败时通知", true),
        SubField.Slider("兜底轮询间隔分", 5.0, 120.0, 15.0, 5.0),
        SubField.Note("优先 webhook 推送，轮询只是兜底；不重复通知同一条失败"),
    )),
    "courier" to SubPage("文件投递", listOf(
        SubField.Input("目标仓库", "xf8410/hualuo-courier"),
        SubField.Slider("切片大小 MB", 8.0, 64.0, 32.0, 4.0),
        SubField.Switch("上传前显示总大小和卷数，等你确认", true),
        SubField.Note("断了自动续传，按 sha256 对账，不重复传"),
    )),
    "audit" to SubPage("二进制审计", listOf(
        SubField.Row("global-metadata.dat", "16.2 MB · IL2CPP v31 ›"),
        SubField.Row("base.apk", "63.5 MB ›"),
        SubField.Switch("重复导入按 sha256 去重", true),
        SubField.Note("审计库只读；大文件分块读，不进内存"),
    )),
    "tasks" to SubPage("定时任务", listOf(
        SubField.Row("夜间备份导出", "02:00 · 启用 ›"),
        SubField.Row("仓库巡检 CI", "每 6h · 暂停 ›"),
        SubField.Row("＋ 新建任务", "说清时间、干什么、发到哪"),
        SubField.Note("每次执行开一个新会话，跑完在列表里留完整现场"),
    )),
    "loop" to SubPage("会话循环", listOf(
        SubField.Row("当前运行", "重构主线 4/20 · 每 300s ›"),
        SubField.Slider("默认间隔秒", 60.0, 3600.0, 300.0, 30.0),
        SubField.Slider("最大轮次", 1.0, 100.0, 20.0, 1.0),
        SubField.Note("检查点存库：崩了重开接着跑，不从头再来"),
    )),
    "relay" to SubPage("多智能体接力", listOf(
        SubField.Row("队伍「代码审查」", "3 棒：写、挑错、定稿 ›"),
        SubField.Row("＋ 新建队伍", "每棒单独选模型"),
        SubField.Seg("接力方式", listOf("顺序接力", "并行后合并"), 0),
        SubField.Switch("每棒署名，产出合成一条回复", true),
        SubField.Note("队伍存库可复用；中途某棒失败自动从该棒重跑，账不混"),
    )),
    "uma" to SubPage("赛马娘工作台", listOf(
        SubField.Sec("内置 SO 连接"),
        SubField.Action(IconKey.ActionPlay, "启动监听与本应用浮窗", "直接读取 127.0.0.1:18765；赛马娘保持前台，不经过浏览器或其他 App"),
        SubField.Action(IconKey.ActionCycle, "自动分析开关", "关键状态变化后复用内置后台生成引擎；最短间隔 20 秒"),
        SubField.Action(IconKey.ActionSparkle, "立即分析", "读取一致快照，并让默认模型使用全部 uma_* 工具分析"),
        SubField.Action(IconKey.ActionRefresh, "立即刷新", "只请求当前 /summary，不调用模型"),
        SubField.Action(IconKey.ActionStop, "停止工作台", "停止后台监听、通信观测并移除游戏浮窗"),
        SubField.Sec("Session 导出"),
        SubField.Head("保存完整 Session ZIP", "输入 Session ID 后由 Android 系统文件选择器选择保存目录和文件名；不会固定写入下载目录。"),
        SubField.BigInput("Session ID（例：1786133409049-22481）"),
        SubField.Button("选择位置并保存", iconKey = IconKey.ActionDownload),
        SubField.Sec("通信协议观测"),
        SubField.Action(IconKey.ActionObserve, "在游戏浮窗中控制", "浮窗底部可直接开始或停止观测。SO 未连接时进入准备状态，游戏启动后自动开启；临时断线后也会自动恢复。"),
        SubField.Sec("怎么读取"),
        SubField.Head("在对话中直接说", "说「读取最近的赛马娘通信端点并按顺序整理」，模型调 uma_protocol_metadata；读业务状态调 uma_get_snapshot、uma_event_observations 等。"),
        SubField.Sec("数据范围"),
        SubField.Head("完整通信数据交给模型", "协议观测返回完整的 path、header、cookie、token、payload 和 hex。本地采集容量由 hlpatch 单独管理。"),
        SubField.Note("红线：一切对游戏只读，不写内存不发包；18767 冻结待拍板"),
    )),
    "memory" to SubPage("记忆", listOf(
        SubField.Row("活跃记忆（当前注入对话）", "1.8 KB ›"),
        SubField.Row("agora重构-地基状态.md", "3.2 KB ›"),
        SubField.Switch("AI 可自动增删记忆", true),
        SubField.Note("记忆=AI 自己写的备忘，跨对话生效；这里能看能删"),
    )),
    "datactl" to SubPage("数据控制", listOf(
        SubField.Row("立即导出", "全部对话与附件清单打包成一个文件 ›"),
        SubField.Row("从备份导入", "合并模式，同 id 跳过不覆盖 ›"),
        SubField.Seg("自动备份", listOf("关", "每天", "每周"), 1),
        SubField.Input("备份目录", "Download/hualuo-backups/"),
    )),
    "storage" to SubPage("存储占用", listOf(
        SubField.Row("对话", "1.9 GB ›"),
        SubField.Row("附件", "0.5 GB ›"),
        SubField.Row("缓存（可清）", "0.1 GB · 清理 ›"),
        SubField.Note("备份不加密（拍板 D-10），存放位置自己负责"),
    )),
    "sites" to SubPage("常用网站", listOf(
        SubField.Row("mine · xf8410/hualuo-repo-tool", "自己的仓 ›"),
        SubField.Row("external · github.com/MetaCat", "别人的 ›"),
        SubField.Row("＋ 添加", "跟 AI 说一句「收藏这个链接」也行"),
    )),
    "roadmap" to SubPage("待开发任务", listOf(
        SubField.Row("todo · 设置页语音输入挪聊天框", "已定稿待实现 ›"),
        SubField.Row("doing · 分卷上传接线", "差最后一步 ›"),
        SubField.Row("done · 版本单源", "CI 已判 ›"),
        SubField.Row("＋ 记一条"),
    )),
    "appearance" to SubPage("外观", listOf(
        SubField.Seg("主题", listOf("亮色", "暗色", "跟随系统"), 2),
        SubField.Seg("主色", listOf("蓝", "青", "紫", "橙"), 0),
        SubField.Switch("跟随壁纸动态取色", false),
        SubField.Note("语义色（成功绿/错误红）不随主色变，防花"),
    )),
    "lang" to SubPage("语言", listOf(
        SubField.Seg("界面语言", listOf("跟随系统", "简体中文", "English"), 1),
        SubField.Note("只换界面文字；AI 回复语言看系统指令"),
    )),
    "about" to SubPage("关于", listOf(
        SubField.Row("版本", "%VERSION% · 单源 ›"),
        SubField.Row("检查更新", "›"),
        SubField.Switch("崩溃报告：本地留一份，启动问一次", true),
        SubField.Row("提 issue", "xf8410/hualuo-repo-tool ›"),
        SubField.Row("开源许可", "›"),
        SubField.Row("给个好评"),
    )),
)
