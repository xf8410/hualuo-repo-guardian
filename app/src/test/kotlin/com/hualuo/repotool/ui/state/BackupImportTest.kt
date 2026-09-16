package com.hualuo.repotool.ui.state

import com.hualuo.repotool.backup.BackupGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份导入走**活通道**的账：设置逐键进 persist（不是绕过它直接写文件——那会被
 * 下一次 flush 用旧值盖掉）、修订号推进、格式不对时一个键都不动、坏值出声不静默。
 */
class BackupImportTest {

    private class MemPersist(initial: Map<String, String> = emptyMap()) : UiPersistence {
        val map = initial.toMutableMap()
        var flushed = 0
        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) { map[key] = value }
        override fun flush(): String? { flushed += 1; return null }
        override fun drainMessages(): List<String> = emptyList()
    }

    private fun state(persist: UiPersistence) = AppUiState(persist)

    @Test
    fun settingsFlowThroughTheLiveChannel() {
        val persist = MemPersist(mapOf("provider.base_url" to "https://old.example.com"))
        val s = state(persist)
        val msg = s.applyImportedBackup(
            BackupGateway.ImportedBackup(
                formatOk = true,
                settingsText = "provider.name=新名字\nprovider.base_url=https://new.example.com\nui.ci_notify_on=true",
                sessionsImported = 3,
                sessionsSkipped = 1,
                warnings = emptyList(),
            ),
        )

        assertEquals("活通道吃到新值", "新名字", persist.map["provider.name"])
        assertEquals("同键覆盖", "https://new.example.com", persist.map["provider.base_url"])
        assertEquals("新键补齐", "true", persist.map["ui.ci_notify_on"])
        assertEquals(1, persist.flushed)
        assertTrue("修订号要推进，界面才吃到新值", s.settingsRevision > 0)
        assertTrue("账要报全：$msg", msg.contains("设置 3 项"))
        assertTrue(msg.contains("会话 3 份"))
        assertTrue(msg.contains("重名会话跳过 1 份"))
    }

    @Test
    fun foreignFormatIsRefusedWithoutTouchingAnything() {
        val persist = MemPersist(mapOf("provider.name" to "原来的"))
        val s = state(persist)
        val msg = s.applyImportedBackup(
            BackupGateway.ImportedBackup(
                formatOk = false,
                settingsText = null,
                sessionsImported = 0,
                sessionsSkipped = 0,
                warnings = listOf("备份版本是 9，本机只认 1：没有导入任何东西"),
            ),
        )
        assertEquals("一个键都不许动", "原来的", persist.map["provider.name"])
        assertEquals(0, persist.flushed)
        assertTrue(msg.contains("没有导入任何东西"))
        assertTrue(msg.contains("版本"))
    }

    @Test
    fun unreadableSettingsDoNotBlockSessions() {
        // 属性解析真会炸的写法：坏的 \\u 转义（IllegalArgumentException），不是普通乱文
        val persist = MemPersist()
        val s = state(persist)
        val msg = s.applyImportedBackup(
            BackupGateway.ImportedBackup(
                formatOk = true,
                settingsText = "key=\\uZZZZ",
                sessionsImported = 2,
                sessionsSkipped = 0,
                warnings = emptyList(),
            ),
        )
        assertTrue("会话账照报：$msg", msg.contains("会话 2 份"))
        assertTrue("设置读不懂要明说", msg.contains("设置读不懂"))
        assertTrue("且声明设置没动", msg.contains("没动"))
    }
}
