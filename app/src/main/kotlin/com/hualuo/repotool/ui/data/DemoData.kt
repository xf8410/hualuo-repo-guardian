package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.Attachment
import com.hualuo.repotool.ui.model.Badge
import com.hualuo.repotool.ui.model.ChatMsg
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.InfoRow
import com.hualuo.repotool.ui.model.ModelRow
import com.hualuo.repotool.ui.model.TaskRow
import com.hualuo.repotool.ui.model.Tone
import com.hualuo.repotool.ui.model.ToolState

// 演示数据：逐条照抄 ui/v13.html（v13.1）里的静态内容。
// 后端（会话库/任务库/CI 客户端/观测桥）接线之前，界面先按这份数据跑通全部交互；
// 接线之后这些 val 换成仓库/接口来的 state，形状不变。

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
        thinkLabel = "▸ 思考过程（7.2s）",
        thinkBody = "1) 先拉 CI 列表与最近提交；2) 核对分卷 manifest 的 sha256 链；" +
            "3) 校验 APK 体积与签名；4) 汇总结论并标注未验证项。",
        toolLine = "\uD83D\uDD2D github_get_workflow_runs → success · 1.2s",
        attachments = listOf(
            Attachment("\uD83D\uDCC4", "manifest.json"),
            Attachment("\uD83D\uDDBC", "shot.png"),
            Attachment("\uD83D\uDCD5", "PDF 3/12 页"),
            Attachment("\uD83C\uDFAC", "视频抽帧 ×8"),
            Attachment("❓", "未识别 type（占位不丢）", unknown = true),
        ),
        dropLabel = "⚠ 本回合丢弃：历史裁剪 96 条（点开查看）",
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

val DemoRepoRows: List<InfoRow> = listOf(
    InfoRow("CI run 34691352476", "success · e7ef4682", Tone.Ok),
    InfoRow("三道闸门", "配平✓ 计数✓ 变异✓", Tone.Ok),
)

val DemoObsRows: List<InfoRow> = listOf(
    InfoRow("127.0.0.1:18765", "在线 · 只读", Tone.Ok),
    InfoRow("18766 push→浮窗", "未启用", Tone.Warn),
)

/** 输入区演示态（v13 composer 顶部两条横幅）。 */
val DemoLoopBar = "\uD83D\uDD01 会话循环 4/20 · 每 300s"
val DemoQueueBar = "⏳ 排队中 1 条：「继续挖 phase2」"
val DemoComposerThumbs = listOf("\uD83D\uDDBC", "\uD83D\uDCC5", "❓")

/** 顶栏上下文账本（原型固定演示值；接线后换成真实 token 计数）。 */
val DemoCtx = "ctx 21.4k/1M · 发 856"

/** ＋ 附件菜单三项（v13 addmenu）。 */
val DemoAttachMenu = listOf(
    "\uD83D\uDDBC 照片",
    "\uD83C\uDFAC 视频（自动抽帧）",
    "\uD83D\uDCDE 文件（PDF 选页）",
)
