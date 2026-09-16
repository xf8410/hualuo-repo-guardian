package com.hualuo.repotool.notify

import com.hualuo.engine.github.GitHubRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CI 提醒判定器的账（纯 JVM）：跑完才出声、id 没变闭嘴、在跑不记不响、
 * 红绿各说各话、首次也响（装上就该知道当前是红是绿，不装神秘）。
 */
class CiNotifyDeciderTest {

    private fun run(
        id: Long,
        sha: String = "abcdef1234567890",
        status: String = "completed",
        conclusion: String? = "success",
    ) = GitHubRun(id = id, name = "自检", headSha = sha, status = status, conclusion = conclusion, createdAt = "")

    @Test
    fun firstSightAnnouncesAndRecords() {
        val decision = CiNotifyDecider(null).decide(listOf(run(111)), "a/b")
        assertEquals("CI 绿了", decision.title)
        assertEquals("a/b · abcdef1 · success · run 111", decision.detail)
        assertEquals(111L, decision.newLastId)
    }

    @Test
    fun sameIdStaysSilent() {
        val decision = CiNotifyDecider("111").decide(listOf(run(111)), "a/b")
        assertNull("见过的 run 不再响", decision.title)
        assertNull(decision.newLastId)
    }

    @Test
    fun newerRunAnnounces() {
        val decision = CiNotifyDecider("111").decide(listOf(run(222, conclusion = "failure")), "a/b")
        assertEquals("CI 红了", decision.title)
        assertEquals("failure 结局要原样上卡", "a/b · abcdef1 · failure · run 222", decision.detail)
        assertEquals(222L, decision.newLastId)
    }

    @Test
    fun stillRunningNeitherRingsNorRecords() {
        val decision = CiNotifyDecider("111").decide(listOf(run(333, status = "in_progress", conclusion = null)), "a/b")
        assertNull("在跑的不响", decision.title)
        assertNull("在跑的也不记账：记了它跑完这轮就永远安静", decision.newLastId)
    }

    @Test
    fun nothingToLookAtStaysSilent() {
        val decision = CiNotifyDecider(null).decide(emptyList(), "a/b")
        assertNull(decision.title)
        assertNull(decision.newLastId)
    }

    @Test
    fun garbageLastSeenIdDoesNotCrashAndTreatsAsFirstSight() {
        val decision = CiNotifyDecider("不是数字").decide(listOf(run(444)), "a/b")
        assertEquals("CI 绿了", decision.title)
        assertEquals(444L, decision.newLastId)
    }
}
