package com.hualuo.repotool.ui.state

import com.hualuo.engine.api.LineSink
import com.hualuo.engine.api.WireRequest
import com.hualuo.engine.api.WireResponse
import com.hualuo.engine.api.WireTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatRuntime 的同步流程测试：worker 注入成「当场跑完」，假 transport 按脚本喂 SSE 帧，
 * 钉的是界面层的几件事——没配置不碰网络、流式落到卡片、错误卡不进历史、忙时不双发、
 * 拉清单三态各归各家。
 *
 * 与 engine 那边分工不重叠：重试红线/槽/卡死归 ChatWireRunnerTest，
 * 这里只管「消息列表与清单这块界面事实演得对不对」。
 */
class ChatRuntimeTest {

    private class MemPersist(initial: Map<String, String> = emptyMap()) : UiPersistence {
        val map = initial.toMutableMap()
        var flushes = 0

        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) { map[key] = value }
        override fun flush(): String? { flushes += 1; return null }
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

    /** 列表那种整份 JSON 的收场：按行喂给 sink，返回 200。 */
    private fun jsonOf(body: String) = { _: WireRequest, sink: LineSink ->
        sink.onLine(body)
        WireResponse(200, null, body.length.toLong(), null)
    }

    private fun runtimeOf(
        persist: UiPersistence,
        wire: FakeWire,
    ): ChatRuntime = ChatRuntime(
        persist,
        transportFactory = { wire },
        worker = { thread -> thread.run() },
        clock = { 1_700_000_000_000L },
    )

    private val configured = mapOf(
        "provider.base_url" to "https://gw.example.com",
        "provider.name" to "测试端",
    )

    @Test
    fun sendWithoutBaseUrlProducesErrorCardAndTouchesNoNetwork() {
        val wire = FakeWire(mutableListOf())
        val runtime = runtimeOf(MemPersist(), wire)

        runtime.send("你好", "m1")

        assertEquals("两条卡：用户气泡 + 错误卡", 2, runtime.messages.size)
        assertTrue("缺配置必须落成错误卡", runtime.messages[1].isError)
        assertTrue(
            "卡上得指名缺哪一格：${runtime.messages[1].text}",
            runtime.messages[1].text.contains("base URL"),
        )
        assertEquals("没配置不许碰网络", 0, wire.calls)
        assertFalse("收场忙灯必须归位", runtime.busy)
    }

    @Test
    fun happyPathStreamsIntoAssistantCard() {
        val wire = FakeWire(mutableListOf(streamOf("早", "啊")))
        val runtime = runtimeOf(MemPersist(configured), wire)

        runtime.send("早上好", "qwen3.8-flash")

        assertEquals(2, runtime.messages.size)
        val user = runtime.messages[0]
        assertTrue("第一条是用户气泡", user.fromMe)
        assertEquals("早上好", user.text)
        val answer = runtime.messages[1]
        assertFalse("成功收场不许带错误样式", answer.isError)
        assertEquals("流式片段拼到同一张卡", "早啊", answer.text)
        assertEquals("署名是设置里的端点名", "测试端", answer.who.first().text)
        assertEquals(1, wire.calls)
        assertFalse(runtime.busy)
    }

    @Test
    fun errorCardsStayOutOfHistory() {
        val wire = FakeWire(
            mutableListOf(
                streamOf("收到"),
                statusOf(500, "bad gateway"),
                streamOf("第三次成了"),
            ),
        )
        val runtime = runtimeOf(MemPersist(configured), wire)

        runtime.send("第一条", "m")
        runtime.send("第二条", "m")
        assertTrue(
            "失败的收场要能在卡上看见原因",
            runtime.messages.last().isError && runtime.messages.last().text.contains("500"),
        )
        runtime.send("第三条", "m")

        val third = wire.seen[2].body!!
        assertEquals("历史里三条用户话", 3, Regex("\"role\":\"user\"").findAll(third).count())
        assertEquals("助手只带成功说过的那条", 1, Regex("\"role\":\"assistant\"").findAll(third).count())
        assertFalse("错误卡的解释话不许喂回模型", third.contains("bad gateway"))
        assertFalse(third.contains("服务出错"))
        assertTrue("第三次的回答正常落地", runtime.messages.last().text.contains("第三次成了"))
    }

    @Test
    fun secondSendWhileBusyIsIgnoredNotQueued() {
        var attempted = false
        val wire = FakeWire(
            mutableListOf(
                { _: WireRequest, sink: LineSink ->
                    // 在生成线程的当口再按发送：必须被忙闸挡下，不排队也不并发
                    if (!attempted) {
                        attempted = true
                        runtimeProxy?.send("插队", "m")
                    }
                    sink.onLine("data: [DONE]")
                    WireResponse(200, null, 10L, null)
                },
            ),
        )
        val runtime = runtimeOf(MemPersist(configured), wire).also { runtimeProxy = it }

        runtime.send("正主", "m")

        assertEquals("被挡下的发送不留痕", 2, runtime.messages.size)
        assertEquals(1, wire.calls)
        assertFalse("消息里不该出现插队", runtime.messages.any { it.text.contains("插队") })
        runtimeProxy = null
    }

    @Test
    fun stopWhenIdleIsHarmless() {
        val runtime = runtimeOf(MemPersist(configured), FakeWire(mutableListOf(streamOf("行"))))
        runtime.stop()
        assertFalse(runtime.busy)
        assertTrue(runtime.messages.isEmpty())
    }

    @Test
    fun refreshModelsLandsRemoteList() {
        val wire = FakeWire(
            mutableListOf(jsonOf("""{"data":[{"id":"m-a"},{"id":"m-b"},{"name":"m-c"}]}""")),
        )
        val runtime = runtimeOf(MemPersist(configured), wire)

        runtime.refreshModels()

        assertEquals("三形状混出的名字全落地（name 兜底同规则）", listOf("m-a", "m-b", "m-c"), runtime.remoteModels)
        assertNull("成功不留错话", runtime.modelsError)
        assertFalse("拉完忙灯归位", runtime.modelsBusy)
        assertEquals("列表是免费 GET：不占生成槽、不碰 chat 通道", 0, runtime.messages.size)
    }

    @Test
    fun refreshModelsErrorKeepsWayOut() {
        val wire = FakeWire(
            mutableListOf(statusOf(401, """{"error":{"message":"bad key"}}""")),
        )
        val runtime = runtimeOf(MemPersist(configured), wire)

        runtime.refreshModels()

        assertTrue("失败不许留旧名单冒充成功", runtime.remoteModels.isEmpty())
        assertTrue(
            "错话要带 401 与出路：${runtime.modelsError}",
            runtime.modelsError?.contains("401") == true,
        )
        assertFalse("失败收场忙灯也要归位", runtime.modelsBusy)
    }

    @Test
    fun refreshModelsEmptyListingSaysSoNotSilence() {
        val wire = FakeWire(mutableListOf(jsonOf("""{"data":[]}""")))
        val runtime = runtimeOf(MemPersist(configured), wire)

        runtime.refreshModels()

        assertTrue("空名单不许装成清单", runtime.remoteModels.isEmpty())
        assertTrue(
            "空名单要单说一句，不许和「拉取成功」混在一起：${runtime.modelsError}",
            runtime.modelsError?.contains("没认出") == true,
        )
    }

    private companion object {
        /** 忙闸测试要在 transport 回调里引用 runtime（构造顺序先 wire 后 runtime），用一次性桥接。 */
        var runtimeProxy: ChatRuntime? = null
    }
}
