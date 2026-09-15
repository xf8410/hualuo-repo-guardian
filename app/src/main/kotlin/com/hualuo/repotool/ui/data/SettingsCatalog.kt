package com.hualuo.repotool.ui.data

import com.hualuo.repotool.ui.model.IconKey
import com.hualuo.repotool.ui.model.SettingsItem
import com.hualuo.repotool.ui.model.SettingsSection
import com.hualuo.repotool.ui.model.SubField
import com.hualuo.repotool.ui.model.SubPage
import com.hualuo.repotool.ui.state.ChatRuntime

/**
 * 「已经接真电」的设置项，和演示表（SettingsData.kt，照抄原型 v13.1）分开放。
 *
 * 为什么分两个文件：演示表是原型的逐行翻译，改它就得同时对原型；而这里每一项都对应设置文件里
 * 一个真键名，关掉 App 再开还在。合并在渲染时做，两边互不污染。
 *
 * 规矩一：能接真电的项一律用 PersistedSwitch / PersistedText，不许再用演示态 ——
 * 后者状态只活在 remember 里，拿它冒充设置就是「绿勾勾撒谎」的同款病。
 * 规矩二：图标只写 IconKey 键名，字形住 res/values/icons.xml（家规，闸门 NoEmojiInSourceTest）。
 * 规矩三：键名跟着**用它的运行层**要（provider 三键来自 ChatRuntime 的常量），
 * 表里再造一份字面量就是给下一次改键留的双源坑。
 */

/** 网关失败要不要自动重发一次的键名（进过真机就不许改，改了老设置读不到）。 */
const val RETRY_COSTLY_KEY = "ui.retry_costly_on_gateway"

/** 默认关：不拿用户的 token 赌运气；失败时给原因，重试按钮在用户手上。 */
const val RETRY_COSTLY_DEFAULT = false

/** 真设置子页（键名到页面）。查表走 subPage()，别直接读这张表。 */
val RealSubPages: Map<String, SubPage> = mapOf(
    "retry" to SubPage(
        "失败与重试",
        listOf(
            SubField.PersistedSwitch(
                "网关掐了自动重发一次",
                RETRY_COSTLY_KEY,
                RETRY_COSTLY_DEFAULT,
            ),
            SubField.Note(
                "开了它：502、504、524 这类网关失败会自动再来一次，代价是这次请求会再花一遍 token。" +
                    "关着（默认）：不替你花钱，把失败原因摊开给你看，重试按钮在你手上。",
            ),
            SubField.Note(
                "两种情况一律不重发，跟开关无关：模型已经吐过字的半截回答（重发会内容重复），" +
                    "以及上下文超限（重发同一份内容只会再错一次）。",
            ),
        ),
    ),
    // 覆盖演示表里那张「提供商」页：现在发送真的走这三格填的东西。
    "provider" to SubPage(
        "提供商",
        listOf(
            SubField.PersistedText(
                "端点名",
                ChatRuntime.KEY_NAME,
                "比如：bai2 网关",
            ),
            SubField.PersistedText(
                "base URL",
                ChatRuntime.KEY_BASE_URL,
                "比如：https://api.example.com/v1",
            ),
            SubField.PersistedText(
                "API 密钥",
                ChatRuntime.KEY_API_KEY,
                "本地端点可以不填",
                secret = true,
            ),
            SubField.Note(
                "这三格就是回合流发送真正读的：没填 base URL 发不出去，会指名道姓告诉你缺哪一格，" +
                    "不会拿演示回答糊弄你。base 自带版本段（/v1、/compatible-mode/v1）就不再补，" +
                    "没带会自动补 /v1。",
            ),
            SubField.Note(
                "密钥明文存在 App 私有目录（2026-09-15 拍板）：非 root 手机上别的 App 读不到；" +
                    "备份文件发给别人看 = 明文可见，这个边界当初是照着「自己用、忘了密码更麻烦」定的。",
            ),
        ),
    ),
)

/** 追加到主页各组的真设置项，key 是演示表里的组 id。图标只写键名。 */
val RealSectionAdditions: Map<String, List<SettingsItem>> = mapOf(
    "s-net" to listOf(
        SettingsItem(
            IconKey.SettingsRetry,
            "失败与重试",
            "网关把连接掐了怎么办；要不要自动重发",
            null,
            "retry",
        ),
    ),
)

/**
 * 主页渲染用的合并结果：同 id 的组只出现一次，真设置项追加在该组演示项后面。
 * 顺序稳定（演示在前、真设置在后），免得每次重组条目乱跳。
 */
fun mergedSettingsSections(): List<SettingsSection> = SettingsSections.map { section ->
    val extra = RealSectionAdditions[section.id].orEmpty()
    if (extra.isEmpty()) section else section.copy(items = section.items + extra)
}

/**
 * 组 id 写错（演示表里没这个组）而挂不上去的条目。
 * 界面启动时必须问一次这个，非空就要出声：悄悄丢一项设置等于用户以为没有这项。
 */
fun orphanAdditions(): List<SettingsItem> {
    val known = SettingsSections.map { it.id }.toSet()
    return RealSectionAdditions.filterKeys { it !in known }.values.flatten()
}

/** 子页查表：真设置表优先，再落回演示表（照原型那份）。未知 key 返回 null，由界面处理。 */
fun subPage(key: String): SubPage? = RealSubPages[key] ?: SubPages[key]
