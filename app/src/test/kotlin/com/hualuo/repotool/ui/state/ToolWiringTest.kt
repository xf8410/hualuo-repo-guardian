package com.hualuo.repotool.ui.state

import com.hualuo.engine.toolcalls.WriteConfirmer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具组装口的契约（0.7.0 刀③）：**不给闸门就没有写工具**——默认拒写的机械证明。
 * 读类十件的注册面顺带钉一下（防以后改接线口时误伤）。纯 JVM，不碰网。
 */
class ToolWiringTest {

    private class MemPersist(initial: Map<String, String> = emptyMap()) : UiPersistence {
        val map = initial.toMutableMap()
        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) { map[key] = value }
        override fun flush(): String? = null
        override fun drainMessages(): List<String> = emptyList()
    }

    @Test
    fun withoutConfirmerTheWriteToolDoesNotExist() {
        val registry = buildGithubToolRegistry(MemPersist())
        val names = registry.specs().map { it.name }
        assertFalse("不给闸门时 github_update_file 不许存在：$names", names.contains("github_update_file"))
    }

    @Test
    fun withConfirmerTheWriteToolIsRegistered() {
        val registry = buildGithubToolRegistry(MemPersist(), WriteConfirmer { false })
        val names = registry.specs().map { it.name }
        assertTrue("给了闸门才注册写工具：$names", names.contains("github_update_file"))
    }

    @Test
    fun readToolsAreRegisteredEitherWay() {
        for (registry in listOf(buildGithubToolRegistry(MemPersist()), buildGithubToolRegistry(MemPersist(), WriteConfirmer { false }))) {
            val names = registry.specs().map { it.name }.toSet()
            assertTrue("读类十件照旧注册：$names", names.contains("github_list_my_repos"))
            assertTrue(names.contains("github_browse_repo"))
            assertTrue(names.contains("github_read_file"))
            assertTrue(names.contains("github_search_code"))
            assertTrue(names.contains("github_ci_runs"))
            assertTrue(names.contains("github_ci_job_log"))
        }
    }
}
