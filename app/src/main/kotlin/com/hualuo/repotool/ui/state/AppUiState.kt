package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.repotool.ui.data.DemoComposerThumbs
import com.hualuo.repotool.ui.data.DemoConversations
import com.hualuo.repotool.ui.model.Conv
import com.hualuo.repotool.ui.model.NavTab

/**
 * 全局界面状态（v13.1 的 JS 变量一对一翻译）。
 * 目前只有「演示态」：所有值可交互但无后端；M2 接线时把这些字段逐个换成仓库/接口来源。
 * 这些增减/弹栈/多选逻辑是纯 JVM 可测的——AppUiStateTest 看着它们，CI 每次跑。
 */
class AppUiState {
    // 底栏五页
    var tab by mutableStateOf(NavTab.Chat)

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

    // 输入区
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
    var micOn by mutableStateOf(false)
    var addMenuOpen by mutableStateOf(false)
    var loopBarOn by mutableStateOf(true)
    var queueBarOn by mutableStateOf(true)
    var thumbs by mutableStateOf(DemoComposerThumbs)
    var currentModel by mutableStateOf("qwen3.8-flash")

    // 原位弹层（模型/工具/任务详情互斥，同原型 closeAll）
    var modelSheetOpen by mutableStateOf(false)
    var toolSheetOpen by mutableStateOf(false)
    var taskSheetKey by mutableStateOf<String?>(null)
    var modelQuery by mutableStateOf("")
    var lockToConversation by mutableStateOf(false)

    // 工具表（本回合）
    var thinkOn by mutableStateOf(true)
    var thinkLevel by mutableStateOf(2)
    var webSearchOn by mutableStateOf(true)
    var shellOn by mutableStateOf(false)
    var codeExecOn by mutableStateOf(false)
    var relayOn by mutableStateOf(false)

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
}
