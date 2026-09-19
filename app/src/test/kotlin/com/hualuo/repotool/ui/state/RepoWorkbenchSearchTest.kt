package com.hualuo.repotool.ui.state

import com.hualuo.engine.github.GitHubCodeHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仓库内搜索的闸门与状态账本测试（全部纯 JVM、绝不碰网）：
 *  - [repoSearchGate] 四道闸逐道钉死：没在浏览、非默认分支、空词、无令牌，缺哪道出哪句话；
 *  - 全过返回 null（放行）；
 *  - 状态舱的离线段：无浏览跑搜出声不发网、退出浏览清场、无浏览点命中被拦。
 * 真网络路径由引擎件（GitHubSearchClientTest）看住。
 */
class RepoWorkbenchSearchTest {

    // ── 四道闸（纯函数逐道钉死） ──────────────────────────────────────────

    @Test
    fun gateBlocksWhenNotBrowsing() {
        val gate = repoSearchGate(browsing = "", ref = null, query = "x", token = "tok")

        assertNotNull(gate)
        assertTrue(gate!!.contains("没在浏览"))
    }

    @Test
    fun gateBlocksOnNonDefaultBranchAndNamesTheBranch() {
        val gate = repoSearchGate(browsing = "o/r", ref = "workbench/foo", query = "x", token = "tok")

        assertNotNull(gate)
        assertTrue(gate!!.contains("默认分支"))
        assertTrue(gate.contains("workbench/foo"))
    }

    @Test
    fun gateBlocksBlankQuery() {
        val gate = repoSearchGate(browsing = "o/r", ref = null, query = "   ", token = "tok")

        assertNotNull(gate)
        assertTrue(gate!!.contains("搜索词"))
    }

    @Test
    fun gateBlocksMissingToken() {
        val gate = repoSearchGate(browsing = "o/r", ref = null, query = "x", token = null)

        assertNotNull(gate)
        assertTrue(gate!!.contains("令牌"))
    }

    @Test
    fun gatePassesWhenEverythingInPlace() {
        assertNull(repoSearchGate(browsing = "o/r", ref = null, query = "LRow", token = "tok"))
    }

    // ── 状态舱的离线段（不碰网） ──────────────────────────────────────────

    @Test
    fun runSearchWithoutBrowseStaysOffline() {
        val s = AppUiState()
        s.repo.searchQuery = "LRow"
        s.repo.runSearch()

        assertFalse(s.repo.searchBusy)
        assertTrue(s.repo.searchHits.isEmpty())
        assertNotNull(s.repo.searchNote)
        assertTrue(s.repo.searchNote!!.contains("没在浏览"))
    }

    @Test
    fun runSearchBlankQuerySaysWriteSomething() {
        val s = AppUiState()
        s.repo.runSearch()

        assertFalse(s.repo.searchBusy)
        assertNotNull(s.repo.searchNote)
    }

    @Test
    fun openSearchHitWithoutBrowseIsBlockedWithoutTouch() {
        val s = AppUiState()
        s.repo.openSearchHit(GitHubCodeHit("a.kt", "a.kt"))

        assertTrue(s.repo.fileViewPath.isEmpty())
        assertFalse(s.repo.fileViewBusy)
    }

    @Test
    fun exitBrowseClearsSearchResults() {
        val s = AppUiState()
        s.repo.exitBrowse()

        assertTrue(s.repo.searchHits.isEmpty())
        assertEquals(0, s.repo.searchTotal)
        assertFalse(s.repo.searchIncomplete)
        assertFalse(s.repo.searchBusy)
        assertNull(s.repo.searchNote)
    }
}
