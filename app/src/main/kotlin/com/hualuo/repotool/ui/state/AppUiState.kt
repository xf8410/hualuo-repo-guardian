package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.repotool.ui.data.DemoComposerThumbs
import com.hualuo.repotool.ui.data.DemoConversations
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.NavTab
import kotlin.reflect.KProperty

/**
 * 全局界面状态（v13.1 的 JS 变量一对一翻译）。
 *
 * 现在的分工：
 *  - 已接真电的字段（tab / input / currentModel / 六个工具开关 / lockToConversation）走 persist：
 *    初值从设置里读，改了就记一笔，落盘时机由界面攒着 flush。
 *  - 仍是演示态的字段（会话列表、附件缩略、toast、弹层）还没后端，M2 会话库那批再换。
 *  - 传 UiPersistence.None（默认）时行为与接线前逐字一致，纯 JVM 测试就这么跑。
 *
 * 键名进过真机就不许改（改了老设置读不到），清单在 UiKeys。
 * 委托一律和声明写在同一行：属性声明在语法上本身就是完整的，把 by 挪到下一行有被当成分句结束的风险，不赌。
 */
class AppUiState(private val persist: UiPersistence = UiPersistence.None) {

    // ── 已接持久化 ──────────────────────────────────────────────────────────

    /** 底栏停在第几页。存枚举名，读不懂就回回合流页。 */
    var tab: NavTab by saved(UiKeys.TAB, readTab(), { it.name })

    /** 输入框草稿（进程被杀、切去别的 App 再回来，不该丢字）。 */
    var input: String by saved(UiKeys.DRAFT, persist.load(UiKeys.DRAFT) ?: "", { it })

    /** 当前模型。 */
    var currentModel: String by saved(UiKeys.MODEL, persist.load(UiKeys.MODEL) ?: DEFAULT_MODEL, { it })

    var thinkOn: Boolean by saved(UiKeys.THINK_ON, readBool(UiKeys.THINK_ON, true), { it.toString() })

    var thinkLevel: Int by saved(UiKeys.THINK_LEVEL, readInt(UiKeys.THINK_LEVEL, 2), { it.toString() })

    var webSearchOn: Boolean by saved(UiKeys.WEB_SEARCH_ON, readBool(UiKeys.WEB_SEARCH_ON, true), { it.toString() })

    var shellOn: Boolean by saved(UiKeys.SHELL_ON, readBool(UiKeys.SHELL_ON, false), { it.toString() })

    var codeExecOn: Boolean by saved(UiKeys.CODE_EXEC_ON, readBool(UiKeys.CODE_EXEC_ON, false), { it.toString() })

    var relayOn: Boolean by saved(UiKeys.RELAY_ON, readBool(UiKeys.RELAY_ON, false), { it.toString() })

    var lockToConversation: Boolean by saved(
        UiKeys.LOCK_TO_CONVERSATION,
        readBool(UiKeys.LOCK_TO_CONVERSATION, false),
        { it.toString() },
    )

    /** 把攒着的改动落盘。返回 null 表示没问题；返回字符串是失败原因，界面必须 toast 出来。 */
    fun flushPersistence(): String? = persist.flush()

    /** 取走设置层累计的坏消息（非法值、半个表情等），取走即清空。 */
    fun persistenceMessages(): List<String> = persist.drainMessages()

    // ── 仍是演示态的字段 ────────────────────────────────────────────────────

    /** 版本串由入口注入（BuildConfig ← version.properties 单源），界面里不许写死。 */
    var versionLabel by mutableStateOf("")

    // 抽屉（会话列表从演示数据起步；删除/新建都作用在这份可变副本上）
    var drawerOpen by mutableStateOf(false)
    var convQuery by mutableStateOf("")
    var selecting by mutableStateOf(false)
    var selectedIds by mutableStateOf(setOf<String>())
    var convs by mutableStateOf(DemoConversations)
    var confirmOpen by mutableStateOf(false)
    var confirmText by mutableStateOf("")
    var confirmAction: (() -> Unit)? = null

    // 输入区的瞬时态（不该持久化）
    var busy by mutableStateOf(false)
    var micOn by mutableStateOf(false)
    var addMenuOpen by mutableStateOf(false)
    var loopBarOn by mutableStateOf(true)
    var queueBarOn by mutableStateOf(true)
    var thumbs by mutableStateOf(DemoComposerThumbs)

    // 原位弹层（模型/工具/任务详情互斥，同原型 closeAll）
    var modelSheetOpen by mutableStateOf(false)
    var toolSheetOpen by mutableStateOf(false)
    var taskSheetKey by mutableStateOf<String?>(null)
    var modelQuery by mutableStateOf("")

    // 设置
    var settingsOpen by mutableStateOf(false)
    var settingsQuery by mutableStateOf("")

    /** 子页栈：空=停在设置主页；栈顶=当前二级页 key（对应原型 SUBSTACK）。 */
    var subStack by mutableStateOf(listOf<String>())

    // toast
    var toastText by mutableStateOf("")
    var toastVisible by mutableStateOf(false)
    private var toastSeq = 0
    var toastToken by mutableStateOf(0)

    fun toast(msg: String) {
        toastText = msg
        toastVisible = true
        toastSeq += 1
        toastToken = toastSeq
    }

    fun closeSheets() {
        modelSheetOpen = false
        toolSheetOpen = false
        taskSheetKey = null
        addMenuOpen = false
        drawerOpen = false
    }

    fun toggleSelect(id: String) {
        val cur = selectedIds
        selectedIds = if (id in cur) cur - id else cur + id
    }

    fun addThumb(icon: String) {
        thumbs = thumbs + icon
    }

    fun removeThumb(index: Int) {
        thumbs = thumbs.filterIndexed { i, _ -> i != index }
    }

    fun newConversation() {
        val c = Conv("c" + (convs.size + 1) + "-" + System.currentTimeMillis(), "新会话 · 刚刚", "刚刚")
        convs = listOf(c) + convs
        selecting = false
        selectedIds = emptySet()
        closeSheets()
        toast("已新建会话（演示数据）")
    }

    fun askDeleteSelected() {
        val n = selectedIds.size
        if (n == 0) return
        confirmText = "删除 $n 个会话？"
        confirmAction = {
            convs = convs.filter { it.id !in selectedIds }
            selectedIds = emptySet()
            selecting = false
            confirmOpen = false
            drawerOpen = false
        }
        confirmOpen = true
    }

    fun openSettings(anchorSub: String? = null) {
        closeSheets()
        settingsQuery = ""
        subStack = if (anchorSub != null) listOf(anchorSub) else emptyList()
        settingsOpen = true
    }

    fun backFromSettings() {
        val s = subStack
        if (s.isNotEmpty()) {
            subStack = s.dropLast(1)
        } else {
            settingsOpen = false
        }
    }

    // ── 持久化小件 ──────────────────────────────────────────────────────────

    /**
     * 「改了就记一笔」的状态位：值没变不记（免得白写盘），变了才既更新界面状态、又交给 persist。
     * 界面读它照样是快照状态，重组行为与 by mutableStateOf 一致。
     */
    private inner class Saved<T>(
        private val key: String,
        initial: T,
        private val encode: (T) -> String,
    ) {
        private val state = mutableStateOf(initial)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            persist.save(key, encode(value))
        }
    }

    private fun <T> saved(key: String, initial: T, encode: (T) -> String): Saved<T> =
        Saved(key, initial, encode)

    private fun readTab(): NavTab {
        val stored = persist.load(UiKeys.TAB) ?: return NavTab.Chat
        return NavTab.entries.firstOrNull { it.name == stored } ?: NavTab.Chat
    }

    private fun readBool(key: String, default: Boolean): Boolean =
        when (persist.load(key)?.trim()?.lowercase()) {
            null -> default
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> default
        }

    private fun readInt(key: String, default: Int): Int =
        persist.load(key)?.trim()?.toIntOrNull() ?: default

    companion object {
        /** 没设置过时的默认模型（真接线后由模型清单决定，这里只是不空着）。 */
        const val DEFAULT_MODEL = "qwen3.8-flash"
    }
}
