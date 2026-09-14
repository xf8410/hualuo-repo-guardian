package com.hualuo.repotool.ui.model

// 界面数据模型：全部照 ui/v13.html（v13.1）的结构翻译成 Kotlin。
// 这一层只有「长什么样」的数据，不含任何业务逻辑；后端接线时换数据来源、不换这些形状。

/** 底栏五页（v13 nav）：回合流 / 长任务 / 工具 / 仓库CI / 观测。 */
enum class NavTab(val title: String, val icon: String) {
    Chat("回合流", "\uD83D\uDCAC"),
    Tasks("长任务", "\u23F1"),
    ToolsPage("工具", "\uD83D\uDD2E"),
    Repo("仓库CI", "\uD83D\uDCE6"),
    Observe("观测", "\uD83D\uDCE1"),
}

/** 徽标色：g=成功绿 y=警告黄 r=错误红 n=中性灰（对应原型 .badge.g/.y/.r）。 */
enum class Tone { Neutral, Ok, Warn, Err }

data class Badge(val text: String, val tone: Tone = Tone.Neutral)

/** 附件：type 未知也必须出现（原型「❓ 未识别 type（占位不丢）」——治旧 Agora 白名单丢附件的病）。 */
data class Attachment(val icon: String, val label: String, val unknown: Boolean = false)

/** 一条助手/系统消息卡（v13 .msg）：徽标行 + 正文 + 思考折叠 + 工具回显 + 附件 + 丢弃行 + 操作。 */
data class ChatMsg(
    val who: List<Badge>,
    val time: String,
    val text: String,
    val fromMe: Boolean = false,
    val thinkLabel: String? = null,
    val thinkBody: String? = null,
    val toolLine: String? = null,
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

/** 设置主页一项（v13 .item）：图标 + 标题 + 一句大白话 + 右侧值 + 下钻 key。 */
data class SettingsItem(val icon: String, val title: String, val desc: String, val value: String?, val subKey: String)

/** 设置主页一组（v13 .sec + 若干 item）。 */
data class SettingsSection(val id: String, val title: String, val items: List<SettingsItem>)

/**
 * 设置二级页的字段类型——一一对应原型 SUB 表里的 r/s/g/i/l/n/sec/a/at/in2/btn：
 * r=普通行（可带跳转） s=开关 g=分段选择 i=输入框 l=滑条 n=底部说明
 * sec=组标题 a=动作卡(图标+标题+描述) at=标题块+说明 in2=大输入框 btn=主按钮
 * radio=单选列表（网页搜索五家用它，照用户截图的「选择搜索提供商」对话框）
 *
 * **两种开关不是一回事，别混用**：
 *  - [Switch] 是**演示态**：状态只活在 `remember` 里，退出子页就没了（原型照搬过来的行）；
 *  - [PersistedSwitch] 是**真设置**：状态经 `AppUiState` 写进设置文件，关掉 App 再开还在。
 * 新接一项就用 [PersistedSwitch]；把 [Switch] 换成它的时候顺带删掉那行的演示数据。
 */
sealed class SubField {
    data class Row(val label: String, val value: String = "", val gotoKey: String? = null) : SubField()

    /** 演示态开关：只活在本次界面的 remember 里，退出即丢。不许拿它冒充真设置。 */
    data class Switch(val label: String, val on: Boolean) : SubField()

    /**
     * 真设置开关：[key] 是设置文件里的键名（ASCII 点分小写），[defaultOn] 是没设置过时的值。
     * 渲染时必须走 `AppUiState` 的按键名读写通道，不许退回本地 remember。
     */
    data class PersistedSwitch(val label: String, val key: String, val defaultOn: Boolean) : SubField()

    data class Seg(val label: String, val options: List<String>, val sel: Int) : SubField()
    data class Input(val label: String, val placeholder: String = "") : SubField()

    /** 滑条：温度/top_p 是小数，超时/内存是整数——统一 Double，步长自己带。 */
    data class Slider(
        val label: String,
        val min: Double,
        val max: Double,
        val value: Double,
        val step: Double = 1.0,
    ) : SubField()

    data class Note(val text: String) : SubField()
    data class Sec(val text: String) : SubField()
    data class Action(val icon: String, val title: String, val desc: String) : SubField()
    data class Head(val title: String, val desc: String) : SubField()
    data class BigInput(val placeholder: String) : SubField()
    data class Button(val text: String) : SubField()
    data class Radio(val label: String, val options: List<RadioChoice>, val selId: String) : SubField()
}

/** 单选一项：名称 + 一句描述 + 是否需要 API Key（照 Agora 内置对话框的文案）。 */
data class RadioChoice(val id: String, val name: String, val desc: String, val needsKey: Boolean)

/** 设置二级页：标题 + 字段表。 */
data class SubPage(val title: String, val fields: List<SubField>)
