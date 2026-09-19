package com.hualuo.repotool.ui.state

import com.hualuo.engine.github.GitHubEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仓库工作台状态舱的纯 JVM 测试：只钉**不发网就能判**的闸门与账本动作——
 * 写法闸拦在撞网之前、目录/文件分家、回退栈、清场、分支/编辑/CI 深看的离线闸。
 * 真网络路径由引擎件测试看住。
 */
class AppUiStateRepoWorkbenchTest {

    @Test
    fun myReposRefreshWithoutTokenSetsNoteNotBusy() {
        val s = AppUiState()
        s.repo.refreshMyRepos()

        assertFalse(s.repo.myReposBusy)
        assertNotNull(s.repo.myReposNote)
        assertTrue(s.repo.myReposNote!!.contains("令牌"))
        assertTrue(s.repo.myRepos.isEmpty())
    }

    @Test
    fun browseOtherRepoRejectsBadQueryWithVoice() {
        val s = AppUiState()
        s.repo.otherRepoQuery = "just-a-name"
        s.repo.browseOtherRepo()

        assertTrue(s.repo.browseRepo.isEmpty())
        assertTrue(s.toastVisible)
    }

    @Test
    fun browseOtherRepoRejectsEmptyQueryWithVoice() {
        val s = AppUiState()
        s.repo.browseOtherRepo()

        assertTrue(s.repo.browseRepo.isEmpty())
        assertTrue(s.toastVisible)
    }

    @Test
    fun browseDownOnFileEntryIsIgnored() {
        val s = AppUiState()
        s.repo.browseDown(GitHubEntry("a.kt", "a.kt", false, 1L))

        assertTrue(s.repo.browseTrail.isEmpty())
        assertTrue(s.repo.browsePath.isEmpty())
        assertFalse(s.repo.browseBusy)
    }

    @Test
    fun browseUpAtRootIsNoop() {
        val s = AppUiState()
        s.repo.browseUp()

        assertTrue(s.repo.browseTrail.isEmpty())
        assertFalse(s.repo.browseBusy)
    }

    @Test
    fun exitBrowseClearsTheWholeBrowseState() {
        val s = AppUiState()
        s.repo.exitBrowse()

        assertTrue(s.repo.browseRepo.isEmpty())
        assertTrue(s.repo.browsePath.isEmpty())
        assertTrue(s.repo.browseTrail.isEmpty())
        assertTrue(s.repo.browseEntries.isEmpty())
        assertTrue(s.repo.fileViewPath.isEmpty())
        assertFalse(s.repo.branchPickerOpen)
        assertFalse(s.repo.commitsOpen)
        assertFalse(s.repo.browseCiOpen)
    }

    @Test
    fun closeFileViewClearsThePreview() {
        val s = AppUiState()
        s.repo.closeFileView()

        assertTrue(s.repo.fileViewPath.isEmpty())
        assertTrue(s.repo.fileViewText == null)
        assertFalse(s.repo.fileViewBusy)
        assertFalse(s.repo.editingOpen)
    }

    @Test
    fun toggleBranchPickerWithoutBrowseStaysOffline() {
        val s = AppUiState()
        s.repo.toggleBranchPicker()

        assertTrue(s.repo.branchPickerOpen)
        // 没在浏览仓库：闸住不发网，清单账本说人话
        assertFalse(s.repo.branchListBusy)
        assertNotNull(s.repo.branchListNote)
        s.repo.toggleBranchPicker()
        assertFalse(s.repo.branchPickerOpen)
    }

    @Test
    fun switchBranchWithoutBrowseIsNoop() {
        val s = AppUiState()
        s.repo.switchBranch("dev")

        assertFalse(s.repo.branchListBusy)
        assertFalse(s.repo.browseBusy)
        assertFalse(s.repo.commitsBusy)
    }

    @Test
    fun toggleCommitsWithoutBrowseStaysOffline() {
        val s = AppUiState()
        s.repo.toggleCommits()

        assertTrue(s.repo.commitsOpen)
        assertFalse(s.repo.commitsBusy)
        s.repo.toggleCommits()
        assertFalse(s.repo.commitsOpen)
    }

    @Test
    fun startEditingWithoutContentIsBlocked() {
        val s = AppUiState()
        s.repo.startEditing()

        assertFalse(s.repo.editingOpen)
    }

    @Test
    fun commitEditWithoutMessageIsBlockedOffline() {
        val s = AppUiState()
        s.repo.editingMessage = "   "
        s.repo.commitEdit()

        // 编辑器没开：闸在第一道，什么请求都不发
        assertFalse(s.repo.editBusy)
    }

    @Test
    fun commitEditWithClosedEditorIsNoop() {
        val s = AppUiState()
        s.repo.commitEdit()

        assertFalse(s.repo.editBusy)
    }

    @Test
    fun cancelEditingClearsTheDraft() {
        val s = AppUiState()
        s.repo.cancelEditing()

        assertFalse(s.repo.editingOpen)
        assertTrue(s.repo.editingText.isEmpty())
        assertTrue(s.repo.editingMessage.isEmpty())
    }

    @Test
    fun toggleBrowseCiWithoutBrowseStaysOffline() {
        val s = AppUiState()
        s.repo.toggleBrowseCi()

        assertTrue(s.repo.browseCiOpen)
        assertFalse(s.repo.ciRunsBusy)
        s.repo.toggleBrowseCi()
        assertFalse(s.repo.browseCiOpen)
    }

    @Test
    fun openRunJobsWithoutBrowseDoesNothing() {
        val s = AppUiState()
        s.repo.openRunJobs(77L)

        assertNull(s.repo.ciJobsRunId)
        assertFalse(s.repo.ciJobsBusy)
    }

    @Test
    fun closeBrowseCiClearsAllThreeLayers() {
        val s = AppUiState()
        s.repo.closeBrowseCi()

        assertFalse(s.repo.browseCiOpen)
        assertNull(s.repo.ciJobsRunId)
        assertTrue(s.repo.ciJobsList.isEmpty())
        assertNull(s.repo.ciLogJobId)
        assertNull(s.repo.ciLogText)
    }
}
