package com.hualuo.repotool.ui.state

import com.hualuo.engine.settings.SettingsStorage
import com.hualuo.engine.settings.SettingsStore
import com.hualuo.repotool.ui.model.NavTab
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面字段 ↔ 设置文件的接线测试（纯 JVM，不启动安卓）。
 *
 * 盯的是这次真正要解决的那件事：**关掉软件再打开，草稿/模型/工具开关不该回到默认值**；
 * 以及老规矩——存不上必须带原因出声、坏数据不许把界面炸了。
 *
 * 半个表情按码位构造（[HALF_EMOJI]），源文件保持纯 ASCII：测试意图不该依赖
 * 「源码里的裸代理字符会被哪个编码器怎么处理」这种和主题无关的事。
 */
class AppUiStatePersistenceTest {

    /** 内存版后端：不碰文件系统，但走的是 engine 里同一套解析/转义/坏消息逻辑。 */
    private class MemoryStorage(initial: String? = null) : SettingsStorage {
        var text: String? = initial
        var writeCount = 0
        var failWith: String? = null

        override fun read(): String? = text

        override fun write(text: String) {
            writeCount += 1
            failWith?.let { throw IOException(it) }
            this.text = text
        }
    }

    private fun bound(storage: MemoryStorage): AppUiState =
        AppUiState(SettingsUiPersistence(SettingsStore(storage)))

    @Test
    fun defaultsHoldWhenNothingWasStored() {
        val state = bound(MemoryStorage())

        assertEquals(NavTab.Chat, state.tab)
        assertEquals("", state.input)
        assertEquals(AppUiState.DEFAULT_MODEL, state.currentModel)
        assertTrue(state.thinkOn)
        assertEquals(2, state.thinkLevel)
        assertTrue(state.webSearchOn)
        assertFalse(state.shellOn)
        assertFalse(state.codeExecOn)
        assertFalse(state.relayOn)
        assertFalse(state.lockToConversation)
        assertTrue(state.persistenceMessages().isEmpty())
    }

    @Test
    fun storedValuesBecomeInitialValues() {
        val storage = MemoryStorage(
            "#format=1\nui.tab=Repo\nui.draft=写到一半的话\nui.model=deepseek-r1\n" +
                "ui.think_on=false\nui.think_level=3\nui.shell_on=true\n",
        )
        val state = bound(storage)

        assertEquals(NavTab.Repo, state.tab)
        assertEquals("写到一半的话", state.input)
        assertEquals("deepseek-r1", state.currentModel)
        assertFalse(state.thinkOn)
        assertEquals(3, state.thinkLevel)
        assertTrue(state.shellOn)
        assertTrue("正常设置不该出声：${state.persistenceMessages()}", state.persistenceMessages().isEmpty())
    }

    @Test
    fun changesSurviveProcessRestart() {
        val storage = MemoryStorage()
        val before = bound(storage)
        before.tab = NavTab.ToolsPage
        before.input = "半截话，还没发出去"
        before.currentModel = "qwen3.8-flash"
        before.thinkLevel = 1
        before.relayOn = true
        assertNull(before.flushPersistence())
        assertEquals("落一次盘", 1, storage.writeCount)

        // 模拟重开软件：同一个后端，重新建一份界面状态
        val after = bound(storage)

        assertEquals(NavTab.ToolsPage, after.tab)
        assertEquals("半截话，还没发出去", after.input)
        assertEquals("qwen3.8-flash", after.currentModel)
        assertEquals(1, after.thinkLevel)
        assertTrue(after.relayOn)
    }

    @Test
    fun sameValueDoesNotRewriteTheFile() {
        val storage = MemoryStorage()
        val state = bound(storage)
        state.input = "一句话"
        assertNull(state.flushPersistence())
        val writes = storage.writeCount

        state.input = "一句话"

        assertNull(state.flushPersistence())
        assertEquals("值没变不该再写盘", writes, storage.writeCount)
    }

    @Test
    fun failedFlushReturnsReasonForTheToast() {
        val storage = MemoryStorage()
        val state = bound(storage)
        state.input = "有内容"
        storage.failWith = "没地方放了"

        val failure = state.flushPersistence()

        assertNotNull("存不上必须给个原因", failure)
        assertTrue("原因得带上后端的话：$failure", failure.orEmpty().contains("没地方放了"))
    }

    @Test
    fun loneSurrogateInDraftIsVoicedNotCrashing() {
        val storage = MemoryStorage()
        val state = bound(storage)
        val dirty = "模型给的半个表情" + HALF_EMOJI + "尾巴"

        state.input = dirty

        val messages = state.persistenceMessages()
        assertEquals("该正好一条：$messages", 1, messages.size)
        assertTrue("得说清是代理位：${messages.first()}", messages.first().contains("代理"))
        assertNull(state.flushPersistence())
        val saved = storage.text.orEmpty()
        assertTrue("文件里必须是转义形式：$saved", saved.contains(ESCAPED_HALF))
        assertFalse("裸半个表情不许进文件", saved.contains(HALF_EMOJI))

        val reopened = bound(MemoryStorage(saved))
        assertEquals("读回来还得逐字相同", dirty, reopened.input)
    }

    @Test
    fun unreadableTabFallsBackToChatSilently() {
        val state = bound(MemoryStorage("#format=1\nui.tab=不存在的页\n"))

        assertEquals(NavTab.Chat, state.tab)
    }

    @Test
    fun nonePersistenceKeepsOldBehaviour() {
        val state = AppUiState()

        state.input = "随便写"
        state.tab = NavTab.Observe
        state.thinkOn = false

        assertNull(state.flushPersistence())
        assertTrue(state.persistenceMessages().isEmpty())
        assertEquals("随便写", state.input)
        assertEquals(NavTab.Observe, state.tab)
    }

    @Test
    fun persistenceRoundTripsThroughStoreText() {
        val store = SettingsStore(MemoryStorage())
        val persistence = SettingsUiPersistence(store)

        persistence.save(UiKeys.DRAFT, "值里有 = 号和中文")
        assertEquals("值里有 = 号和中文", persistence.load(UiKeys.DRAFT))
        assertNull(persistence.flush())

        val revived = SettingsUiPersistence(SettingsStore(MemoryStorage(store.text())))
        assertEquals("值里有 = 号和中文", revived.load(UiKeys.DRAFT))
        assertTrue(revived.drainMessages().isEmpty())
    }

    companion object {
        /** 高位代理码元（半个表情），按码位构造。 */
        private val HALF_EMOJI: String = Char(0xD83D).toString()

        /** 设置文件里该出现的转义写法（engine 用小写十六进制）。 */
        private const val ESCAPED_HALF = "ud83d"
    }
}
