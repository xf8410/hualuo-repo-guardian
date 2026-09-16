package com.hualuo.repotool.ui.state

import com.hualuo.engine.api.LineSink
import com.hualuo.engine.api.WireRequest
import com.hualuo.engine.api.WireResponse
import com.hualuo.engine.api.WireTransport
import com.hualuo.engine.store.SessionStore
import com.hualuo.engine.store.StoredMsg
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话接线的账（M2 第二刀）：**收场即落盘、启动同步回读**。
 * 治的就是旧 Agora 那三条——「退回来消息不见（押退出时机）」「白屏（异步首读）」
 * 「多进几次才出来（回读竞态）」。全部纯 JVM：临时目录当库、假 transport 按脚本喂帧、
 * worker 注入成当场跑完，没有一处靠运气。
 */
class SessionWiringTest {

    private class MemPersist(initial: Map<String, String> = emptyMap()) : UiPersistence {
        val map = initial.toMutableMap()
        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) { map[key] = value }
        override fun flush(): String? = null
        override fun drainMessages(): List<String> = emptyList()
    }

    private class FakeWire(
        private val steps: MutableList<(WireRequest, LineSink) -> WireResponse>,
    ) : WireTransport {
        var calls = 0
        var cancelled = false
        val seen = mutableListOf<WireRequest>()

        override fun exchange(request: WireRequest, sink: LineSink): WireResponse {
            seen += request
            val step = if (steps.size > 1) steps.removeAt(0) else steps[0]
            calls += 1
            return step(request, sink)
        }

        override fun cancel() { cancelled = true }
        override fun isCancelled(): Boolean = cancelled
    }

    private fun streamOf(vararg chunks: String) = { _: WireRequest, sink: LineSink ->
        chunks.forEach { c -> sink.onLine("""data: {"choices":[{"delta":{"content":"$c"}}]}""") }
        sink.onLine("data: [DONE]")
        WireResponse(200, null, 40L, null)
    }

    private fun statusOf(code: Int, body: String) = { _: WireRequest, _: LineSink ->
        WireResponse(code, null, 0L, body)
    }

    private fun tempStore(): SessionStore =
        SessionStore(Files.createTempDirectory("hualuo-sessions").toFile())

    private fun runtimeOf(persist: UiPersistence, wire: FakeWire, store: SessionStore?): ChatRuntime =
        ChatRuntime(
            persist,
            transportFactory = { wire },
            worker = { thread -> thread.run() },
            store = store,
            clock = { 1_700_000_000_000L },
        )

    private val configured = mapOf(
        "provider.base_url" to "https://gw.example.com",
        "provider.name" to "测试端",
    )

    @Test
    fun settledTurnsLandOnDiskAndReloadIdentically() {
        val store = tempStore()
        val wire = FakeWire(mutableListOf(streamOf("早", "啊")))
        val rt = runtimeOf(MemPersist(configured), wire, store)

        rt.send("第一条", "m")

        // 户头必须在：用 error() 解包可空（比起到处 !!，失败时还带人话）
        val id = rt.sessionId ?: error("发过话就该有会话在盘上，session id 不该还是 null")
        assertTrue("会话文件真在盘上", store.exists(id))

        // 模拟杀进程重开：全新 runtime 接同一份库，同步回读
        val rt2 = runtimeOf(MemPersist(configured), FakeWire(mutableListOf()), store)
        val note = rt2.restoreFromStore(id)
        assertEquals("两条都回来", 2, note?.count)
        assertEquals("没有坏行", 0, note?.badLines)
        assertTrue("用户气泡还是用户气泡", rt2.messages[0].fromMe)
        assertEquals("第一条", rt2.messages[0].text)
        assertFalse("成品不许带错误样式", rt2.messages[1].isError)
        assertEquals("早啊", rt2.messages[1].text)

        // 续聊：历史从盘上接上（新请求体里带第一轮对话）
        val wire2 = FakeWire(mutableListOf(streamOf("好")))
        val rt3 = runtimeOf(MemPersist(configured), wire2, store)
        rt3.restoreFromStore(id)
        rt3.send("第二条", "m")
        val body = wire2.seen[0].body!!
        assertTrue("第一轮用户话在", body.contains("第一条"))
        assertTrue("第一轮回答在", body.contains("早啊"))
        assertTrue("第二轮用户话在", body.contains("第二条"))
    }

    @Test
    fun errorCardsStoredButNeverFedBack() {
        val store = tempStore()
        val wire = FakeWire(
            mutableListOf(statusOf(500, "boom"), streamOf("成了")),
        )
        val rt = runtimeOf(MemPersist(configured), wire, store)

        rt.send("第一次", "m")
        rt.send("第二次", "m")

        val body = wire.seen[1].body!!
        assertTrue("第二次的话要发出去", body.contains("第二次"))
        assertFalse(
            "错误卡不进历史（盘上也不许，家规一路贯到底）：$body",
            body.contains("500"),
        )
        val sid = rt.sessionId ?: error("发过话就该有会话在盘上")
        val loaded = store.load(sid)!!
        assertEquals("user+error+user+assistant 四行", 4, loaded.messages.size)
        assertEquals("失败那轮落的是 error 角色", StoredMsg.ROLE_ERROR, loaded.messages[1].role)
        assertEquals("成功那轮落 assistant", StoredMsg.ROLE_ASSISTANT, loaded.messages[3].role)
        assertEquals("成了", loaded.messages[3].text)
    }

    @Test
    fun halfAnswerStoredWithIncompleteFlag() {
        val store = tempStore()
        val wire = FakeWire(
            mutableListOf({ _: WireRequest, sink: LineSink ->
                // 吐了半个字就收线：没有 [DONE]、没有 finish_reason——可证明的不完整
                sink.onLine("""data: {"choices":[{"delta":{"content":"半"}}]}""")
                WireResponse(200, null, 20L, null)
            }),
        )
        val rt = runtimeOf(MemPersist(configured), wire, store)

        rt.send("问", "m")

        val sid = rt.sessionId ?: error("发过话就该有会话在盘上")
        val last = store.load(sid)!!.messages.last()
        assertEquals("半截归 error 角色（喂模型时永远剔掉）", StoredMsg.ROLE_ERROR, last.role)
        assertTrue("半截必须带标记，重开不冒充成品", last.incomplete)
        assertTrue("半截原文要留在卡上：${last.text}", last.text.contains("半截"))
    }

    @Test
    fun appUiStateWiresLatestSessionAndRealCreateAndDelete() {
        val store = tempStore()
        // 先造一份历史，模拟「上次聊过」
        val id = store.create("m")
        store.append(id, StoredMsg(StoredMsg.ROLE_USER, "上次的话", 1_700_000_000_000L))
        store.append(id, StoredMsg(StoredMsg.ROLE_ASSISTANT, "上次的答", 1_700_000_000_001L))

        val state = AppUiState(UiPersistence.None, store)
        assertEquals("启动同步接上上次会话（无异步首读）", 2, state.chat.messages.size)
        assertEquals("抽屉列表来自真库", 1, state.convs.size)
        assertEquals(id, state.convs[0].id)

        state.newConversation()
        assertTrue("新建要出声：${state.toastText}", state.toastText.contains("已新建会话"))
        assertEquals("列表真的长了", 2, state.convs.size)
        assertTrue("新会话文件真在盘上", store.exists(state.convs[0].id))

        // 删会话 = 删文件（不留墓碑），删的是当前正开的那个：户头一并摘掉
        val doomed = state.convs[0].id
        state.selectedIds = setOf(doomed)
        state.askDeleteSelected()
        state.confirmAction?.invoke()
        assertFalse("文件真删了", store.exists(doomed))
        assertEquals("户头摘掉，下条消息自动开新户", null, state.chat.sessionId)
    }

    @Test
    fun unreadableLinesCountOutLoudNotSilentlyDropped() {
        val store = tempStore()
        val id = store.create("m")
        store.append(id, StoredMsg(StoredMsg.ROLE_USER, "好的", 1_700_000_000_000L))
        // 半截坏行：写到一半崩了的标准形状
        store.pathOf(id).appendText("这不是json")

        val state = AppUiState(UiPersistence.None, store)

        assertEquals("坏行跳过不崩，好行照常摆上", 1, state.chat.messages.size)
        assertTrue("坏行必须出声：${state.toastText}", state.toastText.contains("读不出"))
    }
}
