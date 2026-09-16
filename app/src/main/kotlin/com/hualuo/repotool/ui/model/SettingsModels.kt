package com.hualuo.repotool.ui.model

// 设置页的数据模型（从 UiModels 拆出来：一个文件管一件事）。
// 家规：这里只放 IconKey 键名，图形字符住 res/values/icons.xml，源码不留裸字符也不留转义写法。

/** 设置主页一项（v13 .item）：图标键名 + 标题 + 一句大白话 + 右侧值 + 下钻 key。 */
data class SettingsItem(
    val iconKey: IconKey,
    val title: String,
    val desc: String,
    val value: String?,
    val subKey: String,
)

/** 设置主页一组（v13 .sec + 若干 item）。 */
data class SettingsSection(val id: String, val title: String, val items: List<SettingsItem>)

/** 设置二级页：标题 + 字段表。 */
data class SubPage(val title: String, val fields: List<SubField>)

/** 单选一项：名称 + 一句描述 + 是否需要 API Key（照 Agora 内置对话框的文案）。 */
data class RadioChoice(
    val id: String,
    val name: String,
    val desc: String,
    val needsKey: Boolean,
)

/**
 * 设置二级页的字段类型，一一对应原型 SUB 表里的 r/s/g/i/l/n/sec/a/at/in2/btn：
 * r=普通行（可带跳转） s=开关 g=分段选择 i=输入框 l=滑条 n=底部说明
 * sec=组标题 a=动作卡 at=标题块+说明 in2=大输入框 btn=主按钮 radio=单选列表
 *
 * 两种开关不是一回事，别混用：
 *  - Switch 是演示态：状态只活在 remember 里，退出子页就没了（原型照搬过来的行）；
 *  - PersistedSwitch 是真设置：状态经 AppUiState 写进设置文件，关掉 App 再开还在。
 * 新接一项就用 PersistedSwitch；把演示态换成它的时候，顺带删掉那行的演示数据。
 * 文本框同理：Input 是演示态，真设置一律用 PersistedText。
 */
sealed class SubField {
    data class Row(val label: String, val value: String = "", val gotoKey: String? = null) : SubField()

    /** 演示态开关：只活在本次界面的 remember 里，退出即丢。不许拿它冒充真设置。 */
    data class Switch(val label: String, val on: Boolean) : SubField()

    /**
     * 真设置开关：key 是设置文件里的键名（ASCII 点分小写），defaultOn 是没设置过时的值。
     * 渲染时必须走 AppUiState 的按键名读写通道，不许退回本地 remember。
     */
    data class PersistedSwitch(val label: String, val key: String, val defaultOn: Boolean) : SubField()

    /**
     * 真设置文本框：值走 AppUiState 的 text/setText 按键名通道。
     * 编辑即生效、落盘由界面按修订号攒着去抖——输入框不许一个字写一次盘。
     * secret=true 只影响屏显（打点显示）；盘上是否明文由 D-10 的总决定管，不归这个字段管。
     */
    data class PersistedText(
        val label: String,
        val key: String,
        val placeholder: String = "",
        val secret: Boolean = false,
    ) : SubField()

    data class Seg(val label: String, val options: List<String>, val sel: Int) : SubField()
    data class Input(val label: String, val placeholder: String = "") : SubField()

    /** 滑条：温度与 top_p 是小数，超时和内存是整数——统一 Double，步长自己带。 */
    data class Slider(
        val label: String,
        val min: Double,
        val max: Double,
        val value: Double,
        val step: Double = 1.0,
    ) : SubField()

    data class Note(val text: String) : SubField()
    data class Sec(val text: String) : SubField()

    /** 动作卡：图标用键名。 */
    data class Action(val iconKey: IconKey, val title: String, val desc: String) : SubField()

    data class Head(val title: String, val desc: String) : SubField()
    data class BigInput(val placeholder: String) : SubField()

    /**
     * 主按钮：文字必给，图标可选（同样只写键名）。
     * 原型里按钮上那个下载图形原先是拼在文案里的字符，现在拆成 iconKey，
     * 渲染时按「图标 + 空格 + 文字」摆，外观不变。
     *
     * actionKey 非空 = 真动作：点下去经 AppUiState.requestDataAction 发动作请求
     * （由根界面开系统选择器/执行），不给就是演示态按钮。
     */
    data class Button(val text: String, val iconKey: IconKey? = null, val actionKey: String? = null) : SubField()

    data class Radio(val label: String, val options: List<RadioChoice>, val selId: String) : SubField()
}
