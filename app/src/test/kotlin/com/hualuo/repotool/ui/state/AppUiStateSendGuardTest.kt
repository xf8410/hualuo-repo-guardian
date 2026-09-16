package com.hualuo.repotool.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发送闸门（AppUiState.sendCurrentInput）的账：
 * 「单条超大粘贴顶爆上下文窗口」这类事故要在本地拦下，而不是发给对方再吃一张错误卡。
 * 全部纯 JVM 跑（UiPersistence.None），规矩收在状态层一处。
 */
class AppUiStateSendGuardTest {

    @Test
    fun overlongDraftIsRefusedWithChineseGuidanceAndDraftKept() {
        val state = AppUiState()
        val overlong = "好".repeat(AppUiState.MAX_PROMPT_CHARS + 1)
        state.input = overlong

        state.sendCurrentInput()

        assertTrue("必须出声说明为什么拒：${state.toastText}", state.toastText.contains("超了单条上限"))
        assertTrue("出路必须是拆分或精简：${state.toastText}", state.toastText.contains("拆开"))
        assertFalse("拒发不许碰运行层", state.busy)
        assertEquals("草稿是用户打的字，拒发不清空", overlong, state.input)
    }

    @Test
    fun exactLimitPassesButNothingWastefulHappensBeforeIt() {
        val state = AppUiState()
        state.input = "好".repeat(AppUiState.MAX_PROMPT_CHARS)
        state.sendCurrentInput()
        assertEquals("上限内的长文是合法输入，该发就发", "", state.input)
    }

    @Test
    fun blankDraftSaysSoInsteadOfSilentlyDoingNothing() {
        val state = AppUiState()
        state.input = "   "
        state.sendCurrentInput()
        assertTrue(state.toastText.contains("没内容可发"))
        assertFalse(state.busy)
    }

    @Test
    fun normalDraftGoesToRuntimeAndClearsTheBox() {
        val state = AppUiState()
        state.currentModel = AppUiState.DEFAULT_MODEL
        state.input = "第一条真话"
        state.sendCurrentInput()
        assertEquals("发出去草稿就该清", "", state.input)
        assertTrue("用户卡必须已在屏上", state.chat.messages.isNotEmpty())
    }
}
