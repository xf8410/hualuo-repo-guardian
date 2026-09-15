package com.hualuo.repotool.ui.model

import androidx.annotation.StringRes
import com.hualuo.repotool.R

/**
 * 图标键名：代码里只允许出现这里的名字。
 *
 * 图形字符（表情、箭头、勾叉、三横线这些）一律不住在 .kt 里 —— 家规，闸门是
 * `NoEmojiInSourceTest`（裸码位与转义写法一起判红）。它们唯一的住处是
 * `app/src/main/res/values/icons.xml`，那里用 XML 数字字符引用写，资源文件本身也是纯 ASCII。
 *
 * 为什么中间还要这么一层枚举，而不是让数据文件直接写 R.string.icon_xxx：
 *  - 数据表（SettingsData 那份）是纯 Kotlin 的静态表，写死资源 id 以后，改名/删键没有编译期反馈；
 *  - 有了键名，界面渲染处 `stringResource(key.resId)` 取值，将来把字形换成矢量图标
 *    只改这张表与资源，调用点一行都不动。
 *
 * 界面外观不变：每个键对应的码位与原先写在 Kotlin 里的完全一致。
 */
enum class IconKey(@param:StringRes val resId: Int) {
    // 底栏五页
    NavChat(R.string.icon_nav_chat),
    NavTasks(R.string.icon_nav_tasks),
    NavTools(R.string.icon_nav_tools),
    NavRepo(R.string.icon_nav_repo),
    NavObserve(R.string.icon_nav_observe),

    // 顶栏与通用
    Menu(R.string.icon_menu),
    Back(R.string.icon_back),
    Chevron(R.string.icon_chevron),
    Search(R.string.icon_search),

    // 输入区与胶囊上的小图形
    Plus(R.string.icon_plus),
    DotsV(R.string.icon_dots_v),
    Dot(R.string.icon_dot),
    CaretDown(R.string.icon_caret_down),
    CaretUp(R.string.icon_caret_up),
    SendArrow(R.string.icon_send_arrow),
    ExpandScreen(R.string.icon_expand_screen),
    Cross(R.string.icon_cross),

    // 设置主页 27 项
    SettingsProvider(R.string.icon_settings_provider),
    SettingsModel(R.string.icon_settings_model),
    SettingsPrompt(R.string.icon_settings_prompt),
    SettingsGen(R.string.icon_settings_gen),
    SettingsTitle(R.string.icon_settings_title),
    SettingsTrim(R.string.icon_settings_trim),
    SettingsCaption(R.string.icon_settings_caption),
    SettingsTranscription(R.string.icon_settings_transcription),
    SettingsImageGen(R.string.icon_settings_imagegen),
    SettingsWebSearch(R.string.icon_settings_websearch),
    SettingsChatSearch(R.string.icon_settings_chatsearch),
    SettingsShell(R.string.icon_settings_shell),
    SettingsGithub(R.string.icon_settings_github),
    SettingsSites(R.string.icon_settings_sites),
    SettingsRoadmap(R.string.icon_settings_roadmap),
    SettingsRelay(R.string.icon_settings_relay),
    SettingsUma(R.string.icon_settings_uma),
    SettingsTasks(R.string.icon_settings_tasks),
    SettingsLoop(R.string.icon_settings_loop),
    SettingsProxy(R.string.icon_settings_proxy),
    SettingsRetry(R.string.icon_settings_retry),
    SettingsMemory(R.string.icon_settings_memory),
    SettingsDataCtl(R.string.icon_settings_datactl),
    SettingsStorage(R.string.icon_settings_storage),
    SettingsAppearance(R.string.icon_settings_appearance),
    SettingsLang(R.string.icon_settings_lang),
    SettingsAbout(R.string.icon_settings_about),

    // 赛马娘工作台动作卡
    ActionPlay(R.string.icon_action_play),
    ActionCycle(R.string.icon_action_cycle),
    ActionSparkle(R.string.icon_action_sparkle),
    ActionRefresh(R.string.icon_action_refresh),
    ActionStop(R.string.icon_action_stop),
    ActionObserve(R.string.icon_action_observe),
    ActionDownload(R.string.icon_action_download),

    // 消息卡与输入区
    Telescope(R.string.icon_telescope),
    PageUp(R.string.icon_page_up),
    Picture(R.string.icon_picture),
    Book(R.string.icon_book),
    Clapper(R.string.icon_clapper),
    Question(R.string.icon_question),
    Warn(R.string.icon_warn),
    Hourglass(R.string.icon_hourglass),
    Expand(R.string.icon_expand),
    Loop(R.string.icon_loop),
    Phone(R.string.icon_phone),
    Clip(R.string.icon_clip),
    Mic(R.string.icon_mic),
    Send(R.string.icon_send),
    ;

    companion object {
        /** 按键名取（数据表用字符串键时兜底；取不到返回 null，由界面决定怎么出声，不许悄悄换成别的图标）。 */
        fun fromKey(key: String): IconKey? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}
