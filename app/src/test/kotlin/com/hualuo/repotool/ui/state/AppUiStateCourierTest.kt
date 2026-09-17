package com.hualuo.repotool.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件投递 B 段的状态层纯 JVM 测试：批账本、三道闸、动作桥三键、目标前缀。
 * 「开始投递」的闸门是接线的核心契约——缺哪道都不许发请求，每道单独钉死。
 * 标识符全 ASCII（家规：中文不进代码），断言只碰行为。
 */
class AppUiStateCourierTest {

    /** 内存版持久化：喂设置键用，行为与真机一致（load/save 都是键值直读直写）。 */
    private class MapPersist : UiPersistence {
        val map = HashMap<String, String>()
        override fun load(key: String): String? = map[key]
        override fun save(key: String, value: String) {
            map[key] = value
        }
        override fun flush(): String? = null
        override fun drainMessages(): List<String> = emptyList()
    }

    @Test
    fun deliverWithoutPicksIsBlockedWithVoice() {
        val s = AppUiState()
        s.requestCourierDeliver()
        assertNull(s.pendingDataAction)
        assertTrue(s.toastVisible)
    }

    @Test
    fun deliverWithoutRepoConfigIsBlockedWithVoice() {
        val s = AppUiState()
        s.addCourierPicks(listOf(CourierPick("a.zip", "content://x/1", false)))
        s.requestCourierDeliver()
        assertNull(s.pendingDataAction)
        assertTrue(s.toastVisible)
    }

    @Test
    fun deliverWithBadRepoShapeIsBlockedWithVoice() {
        val s = AppUiState(MapPersist())
        s.addCourierPicks(listOf(CourierPick("a.zip", "content://x/1", false)))
        s.setText(UiKeys.COURIER_REPO, "just-a-name")
        s.requestCourierDeliver()
        assertNull(s.pendingDataAction)
        assertTrue(s.toastVisible)
    }

    @Test
    fun deliverWithoutTokenIsBlockedWithVoice() {
        val persist = MapPersist()
        val s = AppUiState(persist)
        s.addCourierPicks(listOf(CourierPick("a.zip", "content://x/1", false)))
        s.setText(UiKeys.COURIER_REPO, "xf8410/hualuo-repo-tool")
        s.requestCourierDeliver()
        assertNull(s.pendingDataAction)
        assertTrue(s.toastVisible)
    }

    @Test
    fun deliverWithPicksRepoAndTokenSendsAction() {
        val persist = MapPersist()
        val s = AppUiState(persist)
        s.addCourierPicks(
            listOf(
                CourierPick("a.zip", "content://x/1", false),
                CourierPick("primary:backup", "content://x/tree", true),
            ),
        )
        // 粘整条链接也认：与仓库CI 同一套 normalize（引擎件）
        s.setText(UiKeys.COURIER_REPO, "https://github.com/xf8410/hualuo-repo-tool")
        persist.map[UiKeys.GITHUB_TOKEN] = "tok-from-workbench"
        s.requestCourierDeliver()
        assertEquals(AppUiState.ACTION_COURIER_DELIVER, s.pendingDataAction)
    }

    @Test
    fun tokenFallsBackToGithubWorkbenchTokenWhenCourierSlotBlank() {
        val persist = MapPersist()
        persist.map[UiKeys.GITHUB_TOKEN] = "tok-from-workbench"
        val s = AppUiState(persist)
        assertEquals("tok-from-workbench", s.courierToken())
        s.setText(UiKeys.COURIER_TOKEN, "tok-courier")
        assertEquals("tok-courier", s.courierToken())
    }

    @Test
    fun branchDefaultsToMainAndReadsSetting() {
        val s = AppUiState()
        assertEquals("main", s.courierBranch())
        s.setText(UiKeys.COURIER_BRANCH, "backup")
        assertEquals("backup", s.courierBranch())
    }

    @Test
    fun courierRepoNormalizesPastedLink() {
        val s = AppUiState()
        s.setText(UiKeys.COURIER_REPO, "https://github.com/xf8410/hualuo-repo-tool/")
        assertEquals("xf8410/hualuo-repo-tool", s.courierRepo())
    }

    @Test
    fun picksAddAndClear() {
        val s = AppUiState()
        s.addCourierPicks(listOf(CourierPick("a", "u1", false), CourierPick("d", "u2", true)))
        assertEquals(2, s.courierPicks.size)
        assertTrue(s.courierPicks.first().isTree.not())
        assertTrue(s.courierPicks.last().isTree)
        s.clearCourierPicks()
        assertTrue(s.courierPicks.isEmpty())
    }

    @Test
    fun requestCourierPickSetsBridgeAction() {
        val s = AppUiState()
        s.requestCourierPick(false)
        assertEquals(AppUiState.ACTION_COURIER_PICK_FILES, s.pendingDataAction)
        s.requestCourierPick(true)
        assertEquals(AppUiState.ACTION_COURIER_PICK_TREE, s.pendingDataAction)
    }

    @Test
    fun prefixIsTimestampedDirectoryWithTrailingSlash() {
        val prefix = AppUiState.buildCourierPrefix(0L)
        assertTrue(prefix.startsWith("courier/"))
        assertTrue(prefix.endsWith("/"))
        assertTrue("courier/\\d{8}-\\d{6}/".toRegex().matches(prefix))
        // 同一时刻同前缀：重投同批 = 原地覆盖的账，靠的就是这个确定性
        assertEquals(prefix, AppUiState.buildCourierPrefix(0L))
    }
}
