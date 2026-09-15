package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.Attachment
import com.hualuo.repotool.ui.model.Badge
import com.hualuo.repotool.ui.model.ChatMsg
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.model.InfoRow
import com.hualuo.repotool.ui.model.ModelRow
import com.hualuo.repotool.ui.model.TaskRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.model.ToolState

// 演示数据：逐条照抄 ui/v13.html（v13.1）里的静态内容。
// 后端（会话库/任务库/CI 客户端/观测桥）接线之前，界面先按这份数据跑通全部交互；
// 接线之后这些 val 换成仓库/接口来的 state，形状不变。
//
// 家规：图形字符不进源码。原先拼在文案里的图标一律拆成 IconKey，由渲染处取值摆出去，外观不变；
// 纯当语气用的符号改成文字（下面逐条标了改哪几处）。
// 注意：闸门连注释一起扫，所以这里描述旧写法时也不能贴那个字符本身，一律用「对勾」「箭头」等词。

val DemoMessages: List<ChatMsg> = listOf(
    ChatMsg(
        who = emptyList(),
        time = "",
        text = "把仓库工具的分卷上传接上，再给我看下 CI 状态",
        fromMe = true,
    ),
    ChatMsg(
        who = listOf(
            Badge("qwen3.8-flash · bai2"),
            Badge("结论", Tone.Ok),
            Badge("依据 4"),
            Badge("未验 1", Tone.Warn),
            Badge("思考·高"),
        ),
        time = "08:12",
        text = "分卷引擎已跑通：part_001.zip + manifest.json，断点续传以 sha256 对账。" +
            "CI run 34723037840 绿，APK 63.5MB。",
        // 原样是「小三角 + 思考过程（7.2s）」：小三角是折叠区的图形前缀，改由渲染处取 IconKey.Expand
        thinkLabel = "思考过程（7.2s）",
        thinkBody = "1) 先拉 CI 列表与最近提交；2) 核对分卷 manifest 的 sha256 链；" +
            "3) 校验 APK 体积与签名；4) 汇总结论并标注未验证项。",
        // 原样开头是望远镜图形、中间是右箭头：图形走键名，箭头改成「结果」两个字，意思不变
        toolLine = "github_get_workflow_runs 结果 success · 1.2s",
        toolIconKey = IconKey.Telescope,
        attachments = listOf(
            Attachment(IconKey.PageUp, "manifest.json"),
            Attachment(IconKey.Picture, "shot.png"),
            Attachment(IconKey.Book, "PDF 3/12 页"),
            Attachment(IconKey.Clapper, "视频抽帧 8 张"),
            Attachment(IconKey.Question, "未识别 type（占位不丢）", unknown = true),
        ),
        // 原样开头的警告三角去掉：这一行本来就按警告样式渲染，不需要字符再提醒一遍
        dropLabel = "本回合丢弃：历史裁剪 96 条（点开查看）",
        dropBody = "被裁剪 96 条：早期天气查询 12 条、参数调试 51 条、日志粘贴 33 条；" +
            "均未参与本轮上下文，附件与结论不受影响。",
        ops = listOf("复制", "重新生成", "引用", "分享"),
    ),
    ChatMsg(
        who = listOf(Badge("系统", Tone.Err)),
        time = "08:15",
        text = "传输中断（524）。工具已成功，无需重做：最后提交 7d551d4a。已自动续跑。",
        isError = true,
    ),
)

val DemoConversations: List<Conv> = listOf(
    Conv("c1", "重构主线 · 分支挖掘", "今天 08:15 · 38 回合"),
    Conv("c2", "仓库工具 M1.5 设计", "昨天 22:40 · 12 回合"),
    Conv("c3", "赛马娘 Uma 训练", "09-12 · 未接管"),
    Conv("c4", "备份排查", "09-10 · 6 回合"),
)

val DemoModels: List<ModelRow> = listOf(
    ModelRow("qwen3.8-flash", "bai2", "1M", hasTools = true, hasVision = true, isDefault = true),
    ModelRow("qwen3-max", "bai2", "256k", hasTools = true, hasVision = false),
    ModelRow("gpt-4o-mini", "官方", "128k", hasTools = true, hasVision = true),
    ModelRow("claude-sonnet", "官方", "200k", hasTools = true, hasVision = true),
    ModelRow("qwen2.5-coder:32b", "本地", "32k", hasTools = false, hasVision = false),
)

val DemoTasks: List<TaskRow> = listOf(
    TaskRow(
        name = "夜间备份导出", value = "02:00 · 下次 17h",
        status = "启用 · 02:00", last = "今天 02:00 · 成功",
        note = "每次执行开新会话，跑完留完整现场", tone = Tone.Ok,
    ),
    TaskRow(
        name = "仓库巡检 CI", value = "每 6h · 暂停",
        status = "暂停", last = "09-13 18:00 · 手动暂停",
        note = "每 6 小时查一次 CI，失败才通知；恢复要手动", tone = Tone.Warn,
    ),
)

val DemoToolStates: List<ToolState> = listOf(
    ToolState("audit_list", listOf(Tone.Ok, Tone.Ok, Tone.Ok, Tone.Ok)),
    ToolState("courier 文件投递", listOf(Tone.Ok, Tone.Ok, Tone.Ok, Tone.Warn)),
    ToolState("uma_read_endpoint", listOf(Tone.Ok, Tone.Ok, Tone.Err, Tone.Neutral)),
)

// 原样是三个对勾字符（配平、计数、变异各挂一个）：对勾改成文字，读起来也更直白
val DemoRepoRows: List<InfoRow> = listOf(
    InfoRow("CI run 34691352476", "success · e7ef4682", Tone.Ok),
    InfoRow("三道闸门", "配平过 计数过 变异过", Tone.Ok),
)

// 原样是「18766 push 箭头 浮窗」中间夹一个箭头字符：改成「到」，端口与含义不变
val DemoObsRows: List<InfoRow> = listOf(
    InfoRow("127.0.0.1:18765", "在线 · 只读", Tone.Ok),
    InfoRow("18766 push 到浮窗", "未启用", Tone.Warn),
)

/** 输入区演示态（v13 composer 顶部两条横幅）：图形走键名，文字走文案。 */
val DemoLoopIcon = IconKey.Loop
val DemoLoopBar = "会话循环 4/20 · 每 300s"
val DemoQueueIcon = IconKey.Hourglass
val DemoQueueBar = "排队中 1 条：「继续挖 phase2」"

/**
 * 输入区已挂附件的缩略（演示）。
 *
 * 这里存的是 **IconKey 的键名字符串**，不是图形字符：缩略列表要进 AppUiState（可增删的运行时状态），
 * 那边一直是字符串列表，改类型会牵动状态层。渲染处用 `IconKey.fromKey(name)` 解回键名取图，
 * 解不出来按「未识别」处理并出声 —— 不许悄悄换成别的图标。
 */
val DemoComposerThumbs = listOf("Picture", "Clip", "Question")

/** 顶栏上下文账本（原型固定演示值；接线后换成真实 token 计数）。 */
val DemoCtx = "ctx 21.4k/1M · 发 856"

/** 附件菜单三项（v13 addmenu，原样开头是一个全角加号）：图形键名 + 文字。 */
val DemoAttachMenu = listOf(
    IconKey.Picture to "照片",
    IconKey.Clapper to "视频（自动抽帧）",
    IconKey.Phone to "文件（PDF 选页）",
)
