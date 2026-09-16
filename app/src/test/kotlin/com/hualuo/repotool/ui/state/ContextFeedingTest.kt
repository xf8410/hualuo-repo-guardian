package com.hualuo.repotool.ui.state

import com.hualuo.engine.api.LineSink
import com.hualuo.engine.api.WireRequest
import com.hualuo.engine.api.WireResponse
import com.hualuo.engine.api.WireTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文喂养的账（M2 第三刀）：系统指令排最前且不占条数、空指令不多发、
 * 超限砍最旧且**砍数出声**（lastTrimmed）、上限读设置现场生效并被 1-500 收口。
 */
class ContextFeedingTest {

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
        val seen = mutableListOf<WireRequest>()

        override fun exchange(request: WireRequest, sink: LineSink): WireResponse {
            seen += request
            val step = if (steps.size > 1) steps.removeAt(0) else steps[0]
            return step(request, sink)
        }

        override fun cancel() {}
        override fun isCancelled(): Boolean = false
    }

    private fun streamOf(vararg chunks: String) = { _: WireRequest, sink: LineSink ->
        chunks.forEach { c -> sink.onLine("""data: {"choices":[{"delta":{"content":"$c"}}]}""") }
        sink.onLine("data: [DONE]")
        WireResponse(200, null, 40L, null)
    }

    private fun runtimeOf(
        persist: UiPersistence,
        wire: FakeWire,
        maxTurns: Int = 40,
        system: String = "",
    ): ChatRuntime = ChatRuntime(
        persist,
        transportFactory = { wire },
        worker = { thread -> thread.run() },
        maxHistoryTurns = { maxTurns },
        systemPrompt = { system },
        clock = { 1_700_000_000_000L },
    )

    private val configured = mapOf(
        "provider.base_url" to "https://gw.example.com",
        "provider.name" to "测试端",
    )

    @Test
    fun systemPromptLeadsTheRequestAndIsNotCountedAgainstHistory() {
        val wire = FakeWire(mutableListOf(streamOf("答一"), streamOf("答二")))
        val rt = runtimeOf(MemPersist(configured), wire, system = "回答一律中文")

        rt.send("问一", "m")
        rt.send("问二", "m")

        val body = wire.seen[1].body!!
        val systemAt = body.indexOf("\"role\":\"system\"")
        val firstUserAt = body.indexOf("\"role\":\"user\"")
        assertTrue("系统指令要排在最前面：$body", systemAt in 0 until firstUserAt)
        assertTrue(body.contains("回答一律中文"))
        assertEquals("系统指令不占历史条数：两条用户话都在", 2, Regex("\"role\":\"user\"").findAll(body).count())
        assertEquals(1, Regex("\"role\":\"assistant\"").findAll(body).count())
        assertEquals("没超限就不许报砍数", 0, rt.lastTrimmed)
    }

    @Test
    fun blankSystemPromptSendsNothingExtra() {
        val wire = FakeWire(mutableListOf(streamOf("好")))
        val rt = runtimeOf(MemPersist(configured), wire, system = "   ")
        rt.send("问", "m")
        assertFalse("空指令一个字都不多发，不塞空 system 占位", wire.seen[0].body!!.contains("\"role\":\"system\""))
    }

    @Test
    fun overlongHistoryTrimsOldestAndAnnouncesTheCount() {
        val wire = FakeWire(mutableListOf(streamOf("答一"), streamOf("答二"), streamOf("答三")))
        val rt = runtimeOf(MemPersist(configured), wire, maxTurns = 2)

        rt.send("问一", "m")
        rt.send("问二", "m")
        rt.send("问三", "m")

        assertEquals("可带 2 条、手头 4 条：砍 2 条", 2, rt.lastTrimmed)
        val body = wire.seen[2].body!!
        assertFalse("最旧的问一被砍", body.contains("问一"))
        assertTrue("答一跟着被砍（成对砍）", !body.contains("答一"))
        assertTrue("问二留下", body.contains("问二"))
        assertTrue("答二留下", body.contains("答二"))
        assertTrue("问三是新话", body.contains("问三"))
        assertEquals("留下的只有两条用户话", 2, Regex("\"role\":\"user\"").findAll(body).count())
    }

    @Test
    fun turnLimitIsClampedToOneToFiveHundred() {
        // 0 收成 1：只带最后一条历史
        val wireA = FakeWire(mutableListOf(streamOf("答一"), streamOf("答二")))
        val rtA = runtimeOf(MemPersist(configured), wireA, maxTurns = 0)
        rtA.send("问一", "m")
        rtA.send("问二", "m")
        assertEquals(1, rtA.lastTrimmed)
        assertFalse(wireA.seen[1].body!!.contains("问一"))
        assertTrue(wireA.seen[1].body!!.contains("答一"))

        // 9999 收成 9999（上限内）：两条全带，零砍数
        val wireB = FakeWire(mutableListOf(streamOf("答一"), streamOf("答二")))
        val rtB = runtimeOf(MemPersist(configured), wireB, maxTurns = 9999)
        rtB.send("问一", "m")
        rtB.send("问二", "m")
        assertEquals(0, rtB.lastTrimmed)
        assertTrue(wireB.seen[1].body!!.contains("问一"))
    }
}
