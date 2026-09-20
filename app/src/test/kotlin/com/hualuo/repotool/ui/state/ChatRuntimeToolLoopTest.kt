package com.hualuo.repotool.ui.state

import com.hualuo.engine.api.LineSink
import com.hualuo.engine.api.WireRequest
import com.hualuo.engine.api.WireResponse
import com.hualuo.engine.api.WireTransport
import com.hualuo.engine.toolcalls.ToolRegistry
import com.hualuo.engine.toolcalls.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具回合循环的界面层契约（0.7.0 第二刀补齐）：执行、回填、再发一轮；
 * 轮次上限防打转；轮间停止键收得住；工具动作在卡上回显。
 * 全部纯 JVM：假 transport 按脚本喂帧、worker 当场跑完、工具执行器是假的。
 */
class ChatRuntimeToolLoopTest {

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

    private fun streamOf(vararg chunks: String): (WireRequest, LineSink) -> WireResponse =
        { _: WireRequest, sink: LineSink ->
            chunks.forEach { c -> sink.onLine("""data: {"choices":[{"delta":{"content":"$c"}}]}""") }
            sink.onLine("data: [DONE]")
            WireResponse(200, null, 40L, null)
        }

    private fun toolCallStep(id: String, name: String): (WireRequest, LineSink) -> WireResponse =
        { _: WireRequest, sink: LineSink ->
            sink.onLine(
                """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"$id","function":{"name":"$name","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
            )
            WireResponse(200, null, 40L, null)
        }

    private fun echoRegistry(executed: MutableList<String>): ToolRegistry = ToolRegistry().apply {
        register(
            ToolSpec("echo", "测试工具：把收到的参数原样记下来", """{"type":"object","properties":{}}"""),
        ) { args ->
            executed += args
            "echo-result：" + args
        }
    }

    private fun runtimeOf(
        persist: UiPersistence,
        wire: FakeWire,
        registry: ToolRegistry,
        maxRounds: Int = 6,
    ): ChatRuntime = ChatRuntime(
        persist,
        transportFactory = { wire },
        worker = { thread -> thread.run() },
        toolRegistry = registry,
        maxToolRounds = maxRounds,
        clock = { 1_700_000_000_000L },
    )

    private val configured = mapOf(
        "provider.base_url" to "https://gw.example.com",
        "provider.name" to "测试端",
    )

    @Test
    fun toolRoundExecutesFeedsBackAndResends() {
        val executed = mutableListOf<String>()
        val wire = FakeWire(mutableListOf(toolCallStep("call_1", "echo"), streamOf("收工了")))
        val runtime = runtimeOf(MemPersist(configured), wire, echoRegistry(executed))

        runtime.send("问", "m")

        assertEquals("工具执行一次", 1, executed.size)
        assertEquals("两轮请求：带工具的一轮 + 回填结果后的一轮", 2, wire.calls)
        val first = wire.seen[0].body ?: error("第一轮请求体不该缺席")
        assertTrue("第一轮要带 tools 清单：$first", first.contains("\"tools\""))
        val second = wire.seen[1].body ?: error("第二轮请求体不该缺席")
        assertTrue("回填 assistant 的 tool_calls", second.contains("\"tool_calls\""))
        assertTrue("回填 tool 结果（角色对）：$second", second.contains("\"role\":\"tool\""))
        assertTrue("tool_call_id 要对上", second.contains("\"tool_call_id\":\"call_1\""))
        assertTrue("工具结果文本要喂回去", second.contains("echo-result"))
        val last = runtime.messages.last()
        assertFalse("收场的成品卡不许带错误样式", last.isError)
        assertEquals("收工了", last.text)
        assertTrue("工具动作要回显：${last.toolLine}", last.toolLine?.contains("echo") == true)
        assertFalse("收场忙灯必须归位", runtime.busy)
    }

    @Test
    fun toolRoundLimitStopsTheSpin() {
        val executed = mutableListOf<String>()
        val wire = FakeWire(mutableListOf(toolCallStep("call_1", "echo")))
        val runtime = runtimeOf(MemPersist(configured), wire, echoRegistry(executed), maxRounds = 2)

        runtime.send("问", "m")

        assertEquals("上限 2 = 最多发 3 轮（超了才收）", 3, wire.calls)
        assertTrue("打到上限走错误卡收场", runtime.messages.last().isError)
        assertTrue(
            "打转闸的出路是拆小：${runtime.messages.last().text}",
            runtime.messages.last().text.contains("拆小"),
        )
        assertFalse(runtime.busy)
    }

    @Test
    fun stopBetweenToolRoundsStopsTheLoop() {
        val executed = mutableListOf<String>()
        var holder: ChatRuntime? = null
        val registry = ToolRegistry().apply {
            register(ToolSpec("echo", "测试工具", """{"type":"object","properties":{}}""")) { args ->
                executed += args
                holder?.stop()
                "ok"
            }
        }
        val wire = FakeWire(mutableListOf(toolCallStep("call_1", "echo"), streamOf("不该到这一轮")))
        val runtime = runtimeOf(MemPersist(configured), wire, registry)
        holder = runtime

        runtime.send("问", "m")

        assertEquals("轮间停止：不再发下一轮", 1, wire.calls)
        assertTrue("停止按取消收场（不是失败）", runtime.messages.last().isError)
        assertTrue(
            "停止的收场话要说清：${runtime.messages.last().text}",
            runtime.messages.last().text.contains("按了停止"),
        )
        assertFalse(runtime.busy)
    }
}
