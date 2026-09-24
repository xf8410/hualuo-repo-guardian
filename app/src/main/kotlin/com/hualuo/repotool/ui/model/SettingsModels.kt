package com.hualuo.repotool.ui.model

// 设置页的数据模型（从 UiModels 拆出来：一个文件管一件事）。
// 家规：这里只放 IconKey 键名，图形字符住 res/values/icons.xml，源码不留裸字符也不留转义写法。

data class SettingsItem(
    val iconKey: IconKey,
    val title: String,
    val desc: String,
    val value: String?,
    val subKey: String,
)

data class SettingsSection(val id: String, val title: String, val items: List<SettingsItem>)

data class SubPage(val title: String, val fields: List<SubField>)

data class RadioChoice(
    val id: String,
    val name: String,
    val desc: String,
    val needsKey: Boolean,
)

sealed class SubField {
    data class Row(val label: String, val value: String = "", val gotoKey: String? = null) : SubField()
    data class Switch(val label: String, val on: Boolean) : SubField()
    data class PersistedSwitch(val label: String, val key: String, val defaultOn: Boolean) : SubField()
    data class PersistedText(
        val label: String,
        val key: String,
        val placeholder: String = "",
        val secret: Boolean = false,
    ) : SubField()
    object GithubLogin : SubField()
    object ProviderSettings : SubField()
    object ModelSettings : SubField()
    data class Seg(val label: String, val options: List<String>, val sel: Int) : SubField()
    data class Input(val label: String, val placeholder: String = "") : SubField()
    data class Slider(
        val label: String,
        val min: Double,
        val max: Double,
        val value: Double,
        val step: Double = 1.0,
    ) : SubField()
    data class Note(val text: String) : SubField()
    data class Sec(val text: String) : SubField()
    data class Action(val iconKey: IconKey, val title: String, val desc: String) : SubField()
    data class Head(val title: String, val desc: String) : SubField()
    data class BigInput(val placeholder: String) : SubField()
    data class Button(val text: String, val iconKey: IconKey? = null, val actionKey: String? = null) : SubField()
    data class Radio(val label: String, val options: List<RadioChoice>, val selId: String) : SubField()
}
