package com.hualuo.repotool.ui.model

// 界面数据模型：全部照 ui/v13.html（v13.1）的结构翻译成 Kotlin。
// 这一层只有「长什么样」的数据，不含任何业务逻辑；后端接线时换数据来源、不换这些形状。
// 设置页那套模型在 SettingsModels.kt，图标键名在 IconKey.kt。
//
// 家规：图形字符（表情、箭头、勾叉、三横线）不进源码，也不以转义写法出现 ——
// 闸门是 NoEmojiInSourceTest，两样都判。字形唯一的住处是 res/values/icons.xml。

/** 底栏五页（v13 nav）：回合流 / 长任务 / 工具 / 仓库CI / 观测。 */
enum class NavTab(val title: String, val iconKey: IconKey) {
    Chat("回合流", IconKey.NavChat),
    Tasks("长任务", IconKey.NavTasks),
    ToolsPage("工具", IconKey.NavTools),
    Repo("仓库CI", IconKey.NavRepo),
    Observe("观测", IconKey.NavObserve),
}

/** 徽标色：g=成功绿 y=警告黄 r=错误红 n=中性灰（对应原型 .badge.g/.y/.r）。 */
enum class Tone { Neutral, Ok, Warn, Err }

data class Badge(val text: String, val tone: Tone = Tone.Neutral)

/**
 * 附件：type 未知也必须出现（原型「未识别 type（占位不丢）」——治旧 Agora 白名单丢附件的病）。
 * 图标用键名；不认识的类型也给一个键（问号那枚），不许留空假装没有附件。
 */
data class Attachment(val iconKey: IconKey, val label: String, val unknown: Boolean = false)

/** 一条助手/系统消息卡（v13 .msg）：徽标行 + 正文 + 思考折叠 + 工具回显 + 附件 + 丢弃行 + 操作。 */
data class ChatMsg(
    val who: List<Badge>,
    val time: String,
    val text: String,
    val fromMe: Boolean = false,
    val thinkLabel: String? = null,
    val thinkBody: String? = null,
    val toolLine: String? = null,

    /**
     * 工具回显行开头的图形（原型每行都带一枚）。
     * 它和文字分开存：图形走资源键名，工具名与结果走 toolLine 文本。
     */
    val toolIconKey: IconKey? = null,
    val attachments: List<Attachment> = emptyList(),
    val dropLabel: String? = null,
    val dropBody: String? = null,
    val ops: List<String> = emptyList(),
    val isError: Boolean = false,
)

/** 抽屉里的会话行。 */
data class Conv(val id: String, val title: String, val meta: String)

/** 模型弹层一行（v13 .mrow）：分组 + 真实 ctx + 工具/视觉能力。 */
data class ModelRow(
    val name: String,
    val group: String,
    val ctx: String,
    val hasTools: Boolean,
    val hasVision: Boolean,
    val isDefault: Boolean = false,
)

/** 长任务页一行（含详情弹层三字段，对应 data-task/data-st/data-last/data-note）。 */
data class TaskRow(
    val name: String,
    val value: String,
    val status: String,
    val last: String,
    val note: String,
    val tone: Tone,
)

/** 工具页四态：注册 / 接线 / 开关 / 可执行（g/y/r/n 四灯）。 */
data class ToolState(val name: String, val states: List<Tone>)

/** 仓库CI / 观测页的普通行。 */
data class InfoRow(val label: String, val value: String, val tone: Tone = Tone.Ok)
