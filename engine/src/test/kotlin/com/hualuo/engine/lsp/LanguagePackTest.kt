package com.hualuo.engine.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语言包定义件的纯 JVM 契约：构造时硬验的每条纪律各钉一条，
 * 外加一条日志纪律——拒绝消息里不许漏出用户可控的原始控制字符。
 *
 * 为什么验证放在构造时而不是安装时：坏定义越早拒越好。
 * 等包下载完几百 MB 再说「名字里不该有双点」，是把带宽和信任一起浪费掉。
 */
class LanguagePackTest {

    private fun valid(): LanguagePack = LanguagePack(
        id = "kotlin",
        displayName = "Kotlin 语言服务",
        version = "1.0.0",
        serverBinary = "kotlin-language-server",
        sha256Hex = "a".repeat(64),
        downloadUrl = "https://example.com/packs/kotlin-1.0.0.zip",
    )

    @Test
    fun validPackBuilds() {
        val pack = valid()
        assertEquals("kotlin", pack.id)
        assertEquals(64, pack.sha256Hex.length)
    }

    @Test
    fun validEdgeShapesPass() {
        valid().copy(id = "a", version = "0", serverBinary = "s.sh", displayName = "x")
        valid().copy(id = "x".repeat(64))
    }

    @Test
    fun slugRulesRejectBadIds() {
        assertRejected("大写字母不许进 id") { valid().copy(id = "Kotlin") }
        assertRejected("空 id 不许") { valid().copy(id = "") }
        assertRejected("超过 64 位的 id 不许") { valid().copy(id = "a".repeat(65)) }
        assertRejected("斜杠不许进 id") { valid().copy(id = "a/b") }
    }

    @Test
    fun versionRulesRejectBadVersions() {
        assertRejected("空格不许进版本") { valid().copy(version = "1.0 beta") }
        assertRejected("大写不许进版本") { valid().copy(version = "V1") }
        assertRejected("空版本不许") { valid().copy(version = "") }
    }

    @Test
    fun serverBinaryRejectsTraversalAndSeparators() {
        assertRejected("双点遍历不许") { valid().copy(serverBinary = "..\\evil") }
        assertRejected("正斜杠不许") { valid().copy(serverBinary = "bin/server") }
        assertRejected("反斜杠不许") { valid().copy(serverBinary = "bin\\server") }
        assertRejected("空文件名不许") { valid().copy(serverBinary = "") }
    }

    @Test
    fun sha256MustBeLowerHex64() {
        assertRejected("大写哈希视为不匹配") { valid().copy(sha256Hex = "A".repeat(64)) }
        assertRejected("63 位不许") { valid().copy(sha256Hex = "a".repeat(63)) }
        assertRejected("65 位不许") { valid().copy(sha256Hex = "a".repeat(65)) }
        assertRejected("非十六进制字符不许") { valid().copy(sha256Hex = "g".repeat(64)) }
    }

    @Test
    fun urlMustBeHttpOrHttps() {
        assertRejected("ftp 不许") { valid().copy(downloadUrl = "ftp://example.com/x.zip") }
        assertRejected("file 不许") { valid().copy(downloadUrl = "file:///data/x.zip") }
        assertRejected("裸域名不许") { valid().copy(downloadUrl = "example.com/x.zip") }
    }

    @Test
    fun controlCharsInUrlRejected() {
        val message = assertRejected("换行不许溜进下载地址") {
            valid().copy(downloadUrl = "https://example.com/x.zip\n第二行")
        }
        assertTrue("必须点名控制字符：$message", message.contains("控制字符"))
        assertTrue("消息里不许带原始换行：$message", message.indexOf('\n') < 0)
    }

    @Test
    fun displayNameRules() {
        assertRejected("空显示名不许") { valid().copy(displayName = "") }
        assertRejected("控制字符不许进显示名") { valid().copy(displayName = "坏\u0000名") }
    }

    /** 拒绝必须带原因，且原因消息里不许出现原文控制字符（脱敏之后是点号）。 */
    private fun assertRejected(what: String, block: () -> Any?): String {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            val message = e.message ?: ""
            assertTrue("拒绝时必须给原因：$what", message.isNotEmpty())
            return message
        }
        throw AssertionError("本该拒绝但放行了：$what")
    }
}
