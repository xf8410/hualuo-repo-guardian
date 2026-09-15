package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.repotool.ui.data.DemoComposerThumbs
import com.hualuo.repotool.ui.data.DemoConversations
import com.hualuo.repotool.ui.data.RETRY_COSTLY_DEFAULT
import com.hualuo.repotool.ui.data.RETRY_COSTLY_KEY
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.NavTab
import kotlin.reflect.KProperty

/**
 * 全局界面状态（v13.1 的 JS 变量一对一翻译）。
 *
 * 现在的分工：
 *  - 已接真电的字段（tab、input、currentModel、六个工具开关、lockToConversation）走 persist：
 *    初值从设置里读，改了就记一笔，落盘时机由界面攒着 flush。
 *  - 设置页里新加的开关走 flag 与 setFlag：键名由数据表带过来，不占字段位。
 *  - 设置页里的真文本走 text 与 setText：同样按键名，落盘按 settingsRevision 去抖。
 *  - 回合流真消息住 chat（ChatRuntime）：发送走真网络，busy 也从它读，不再单独一个演示布尔。
 *  - 仍是演示态的字段（会话列表、附件缩略、toast、弹层）还没后端，M2 会话库那批再换。
 *  - 传 UiPersistence.None（默认）时行为与接线前逐字一致，纯 JVM 测试就这么跑。
 *
 * 键名进过真机就不许改（改了老设置读不到），清单在 UiKeys、SettingsCatalog 与 ChatRuntime。
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

    // ── 设置页的真开关（按键名，不占字段位） ────────────────────────────────

    /**
     * 本次界面里被改过的开关。只由 setFlag 填，读的时候不写：
     * getOrPut 会在组合期间写状态，那是重组抖动的常见来源，别给后面的人埋。
     */
    private val flagOverrides = mutableStateMapOf<String, Boolean>()

    /** 读一个真开关：本次改过的优先，其次设置文件，最后调用方给的默认值。 */
    fun flag(key: String, defaultOn: Boolean = false): Boolean =
        flagOverrides[key] ?: readBool(key, defaultOn)

    /** 改一个真开关：既更新界面状态（会重组），又记进设置等落盘。 */
    fun setFlag(key: String, value: Boolean) {
        flagOverrides[key] = value
        persist.save(key, value.toString())
    }

    // ── 设置页的真文本（按键名；提供商地址密钥这类） ────────────────────────

    /**
     * 本次改过、还没落盘的文本：输入框每敲一下只写内存，
     * 真落盘由界面按 [settingsRevision] 攒着去抖——「一个字写一次盘」是绝对不许的。
     */
    private val textOverrides = mutableStateMapOf<String, String>()

    /**
     * 每次 setText 给这个版本号加一：自动保存的看护方只看这一个数，
     * 不逐个盯每张输入框——以后文本键增减，看护点也不用跟着改。
     */
    var settingsRevision by mutableStateOf(0L)
        private set

    /** 读一个真文本设置：本次改过的优先，其次设置文件，最后调用方给的默认值。 */
    fun text(key: String, default: String = ""): String =
        textOverrides[key] ?: persist.load(key) ?: default

    /** 改一个真文本：界面立刻更新、内存记一笔（不碰盘），修订号推进等去抖落盘。 */
    fun setText(key: String, value: String) {
        if (text(key) == value) return
        textOverrides[key] = value
        persist.save(key, value)
        settingsRevision += 1
    }

    // ── 回合流真运行层 ──────────────────────────────────────────────────────

    /**
     * 真说过的话与生成槽都住这里（契约见 ChatRuntime）。
     * 「网关失败自动重发」那个真开关当场从 flag 通道读——两边共用一份事实，不各记各的。
     * 必须具名传：尾随 lambda 会绑到 ChatRuntime 的最后一个参数（clock，返回 Long），
     * 拿开关去尾随就是拿 Boolean 冒充 Long——CI 编译段抓到过，别再犯。
     */
    val chat = ChatRuntime(
        persist,
        autoRetryCostly = { flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT) },
    )

    /** 输入区发送钮的忙灯：真在跑才亮，不再是个能手动点着玩的演示布尔。 */
    val busy: Boolean get() = chat.busy

    // ── 仍是演示态的字段 ────────────────────────────────────────────────────

    /** 版本串由入口注入（BuildConfig 读自 version.properties 单源），界面里不许写死。 */
    var versionLabel by mutableStateOf("")

    // 抽屉（会话列表从演示数据起步；删除与新建都作用在这份可变副本上）
    var drawerOpen by mutableStateOf(false)
    var convQuery by mutableStateOf("")
    var selecting by mutableStateOf(false)
    var selectedIds by mutableStateOf(setOf<String>())
    var convs by mutableStateOf(DemoConversations)
    var confirmOpen by mutableStateOf(false)
    var confirmText by mutableStateOf("")
    var confirmAction: (() -> Unit)? = null

    // 输入区的瞬时态（不该持久化）
    var micOn by mutableStateOf(false)
    var addMenuOpen by mutableStateOf(false)
    var loopBarOn by mutableStateOf(true)
    var queueBarOn by mutableStateOf(true)
    var thumbs by mutableStateOf(DemoComposerThumbs)

    // 原位弹层（模型、工具、任务详情互斥，同原型 closeAll）
    var modelSheetOpen by mutableStateOf(false)
    var toolSheetOpen by mutableStateOf(false)
    var taskSheetKey by mutableStateOf<String?>(null)
    var modelQuery by mutableStateOf("")

    // 设置
    var settingsOpen by mutableStateOf(false)
    var settingsQuery by mutableStateOf("")

    /** 子页栈：空表示停在设置主页；栈顶是当前二级页 key（对应原型 SUBSTACK）。 */
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
