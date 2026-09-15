package com.hualuo.engine.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GenerationError] 的账：分类必须**各给各的出路**，而且**任何一条都不许把密钥原样吐给人看**。
 */
class GenerationErrorTest {

    @Test
    fun httpStatusCodesLeadToDifferentNextSteps() {
        val cases = mapOf(
            401 to "密钥",
            403 to "额度",
            404 to "base URL",
            429 to "等",
            500 to "稍后重试",
            413 to "太大",
        )
        val seen = mutableSetOf<String>()
        for ((code, hint) in cases) {
            val message = GenerationError.Network(code, "gateway said no").userMessage()
            assertTrue("$code 的提示要指到「$hint」：$message", message.contains(hint))
            assertTrue("$code 的提示要带状态码：$message", message.contains(code.toString()))
            seen += message
        }
        assertTrue("六条提示不许撞成一句", seen.size == cases.size)
    }

    @Test
    fun unknownStatusStillSaysSomethingUseful() {
        val message = GenerationError.Network(418, "teapot").userMessage()
        assertTrue(message.contains("418"))
        assertTrue(message.contains("teapot"))
    }

    @Test
    fun apiErrorCombinesCodeAndTypeWithoutLeadingJunk() {
        val full = GenerationError.Api("insufficient_quota", "billing_error", "no credits left")
        assertTrue(full.userMessage().startsWith("insufficient_quota billing_error："))
        val bare = GenerationError.Api(null, null, "just text")
        assertTrue(bare.userMessage().contains("just text"))
    }

    @Test
    fun sseParseKeepsAnExcerptInsteadOfSwallowingIt() {
        // 原版这里只剩 "Failed to parse server response."，对方不守规矩时一点线索都没有
        val message = GenerationError.SseParse("<html>502 Bad Gateway</html>", "不是 data: 开头").userMessage()
        assertTrue("要带原文片段：$message", message.contains("502 Bad Gateway"))
        assertTrue("要带原因：$message", message.contains("不是 data: 开头"))
    }

    @Test
    fun incompleteStreamSaysProvablyIncomplete() {
        val plain = GenerationError.IncompleteStream("bai2", null, false, true).userMessage()
        assertTrue(plain.contains("半路断了"))
        assertTrue("已经吐出内容时要提醒别当成品：$plain", plain.contains("别当成品"))
        val withTool = GenerationError.IncompleteStream("bai2", "tool_use", true, false).userMessage()
        assertTrue(withTool.contains("正在写一个工具调用"))
        assertTrue(withTool.contains("tool_use"))
    }

    @Test
    fun truncatedSaysWhichKnobToTurn() {
        val message = GenerationError.OutputTruncated("bai2", "length").userMessage()
        assertTrue(message.contains("最大输出 token"))
        assertTrue(message.contains("思考预算"))
        assertTrue(message.contains("length"))
    }

    @Test
    fun transcriptionNamesItsOwnKind() {
        // 原版把视频/PDF 也报成 "Image transcription failed"
        assertTrue(GenerationError.Transcription("/x/y.pdf", "PDF", "解析不了").userMessage().contains("PDF转写失败"))
        assertTrue(GenerationError.Transcription("/x/y.mp4", "视频", "太长").userMessage().contains("视频转写失败"))
    }

    @Test
    fun configAndCancelledReadLikeHumanSpeech() {
        assertTrue(GenerationError.Configuration("还没填密钥").userMessage().contains("还没填密钥"))
        assertTrue(GenerationError.Cancelled.userMessage().contains("停止"))
        assertTrue(GenerationError.Timeout.userMessage().contains("超时"))
    }

    @Test
    fun unknownExceptionNeverLeaksTheKeyItCarried() {
        // 有些网关把密钥写在查询串上；异常原文进界面之前必须打码
        val boom = IllegalStateException(
            "connect failed https://gw.example.com/v1/chat/completions?api_key=sk-abcdef1234567890ABCDEFGH",
        )
        val message = GenerationError.Unknown(boom).userMessage()
        assertFalse("密钥原文不许出现在给人看的那句里：$message", message.contains("sk-abcdef1234567890ABCDEFGH"))
        assertTrue("但要留得下线索让人认得出是哪次：$message", message.contains("gw.example.com"))
    }

    @Test
    fun longProviderMessagesGetFoldedAndTruncated() {
        val html = "<html>" + "x".repeat(900) + "</html>"
        val message = GenerationError.Network(500, html).userMessage()
        assertTrue("超长要截断：$message", message.contains("已截断"))
        assertTrue(message.length < 400)
        assertFalse("不许留换行把气泡撑歪", message.contains("\n"))
    }

    @Test
    fun maskingHitsSecretsButNotNormalWords() {
        val masked = maskSecrets("Authorization: Bearer sk-proj-AAAAAAAAAAAAAAAAAAAA")
        assertFalse(masked.contains("AAAAAAAAAAAAAAAAAAAA"))
        assertTrue(masked.contains("Bearer"))

        val labeled = maskSecrets("key=1234567890abcdef1234567890abcdef&model=x")
        assertFalse(labeled.contains("1234567890abcdef1234567890abcdef"))

        // 正常内容不许被啃：中文句子、主机名、短模型名
        val plain = "请检查 api.openai.com 上的 gpt-4o-mini 是否可用"
        assertTrue(masked.length > 0)
        assertTrue("正常句子不该动：$plain", maskSecrets(plain) == plain)
    }

    @Test
    fun datedModelNameGetsMaskedOnPurpose() {
        // 取舍要说在前面：20 位以上的字母数字串一律打码，会误伤 `claude-3-5-sonnet-20240620`
        // 这种带日期的模型名。宁可多打码也不能漏密钥 —— 这条不是 bug，是选边。
        val text = "model claude-3-5-sonnet-20240620 not found"
        val masked = maskSecrets(text)
        assertFalse(masked.contains("claude-3-5-sonnet-20240620"))
        assertTrue("短名字不受影响", maskSecrets("model gpt-4o not found") == "model gpt-4o not found")
    }
}
