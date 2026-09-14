package com.hualuo.repotool.ui.state

import com.hualuo.engine.http.FailureClass
import com.hualuo.engine.http.RequestCost
import com.hualuo.engine.http.RetryDecision
import com.hualuo.engine.http.RetryPolicy
import com.hualuo.engine.settings.SettingsStorage
import com.hualuo.engine.settings.SettingsStore
import com.hualuo.repotool.ui.data.RETRY_COSTLY_DEFAULT
import com.hualuo.repotool.ui.data.RETRY_COSTLY_KEY
import com.hualuo.repotool.ui.data.RealSectionAdditions
import com.hualuo.repotool.ui.data.SettingsSections
import com.hualuo.repotool.ui.data.SubPages
import com.hualuo.repotool.ui.data.mergedSettingsSections
import com.hualuo.repotool.ui.data.orphanAdditions
import com.hualuo.repotool.ui.data.subPage
import com.hualuo.repotool.ui.model.SubField
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「失败与重试」这个真设置开关的端到端测试（纯 JVM）：
 * 数据表挂得上、值存得进设置文件、重开得着，而且拨动开关真的改变重试收场。
 * 最后那半条最重要，不然它就是个装饰。
 */
class RetrySettingsTest {

    private class MemoryStorage(initial: String? = null) : SettingsStorage {
        var text: String? = initial
        var writeCount = 0

        override fun read(): String? = text

        override fun write(text: String) {
            writeCount += 1
            this.text = text
        }
    }

    private fun stateWith(storage: MemoryStorage): AppUiState =
        AppUiState(SettingsUiPersistence(SettingsStore(storage)))

    private fun attempt(
        status: Int,
        cost: RequestCost = RequestCost.Costly,
        bytes: Long = 0L,
        body: String = "",
    ) = RetryPolicy.AttemptOutcome(
        attempt = 1,
        cost = cost,
        status = status,
        body = body,
        bytesReceived = bytes,
    )

    @Test
    fun switchDefaultsToOff() {
        assertFalse("默认不替用户花 token", RETRY_COSTLY_DEFAULT)
        assertEquals("ui.retry_costly_on_gateway", RETRY_COSTLY_KEY)
    }

    @Test
    fun offMeansNoAutoResendForCostlyGatewayFailure() {
        val d = retryPolicyFor(false).decide(attempt(status = 524))

        assertTrue("关着就该交回用户：$d", d is RetryDecision.GiveUp)
        val giveUp = d as RetryDecision.GiveUp
        assertEquals(FailureClass.GatewayTimeout, giveUp.failure)
        assertTrue(giveUp.reason.contains("默认不"))
    }

    @Test
    fun onMeansAutoResendForCostlyGatewayFailure() {
        val d = retryPolicyFor(true).decide(attempt(status = 524))

        assertTrue("开着就该重来：$d", d is RetryDecision.Retry)
        assertEquals(2, (d as RetryDecision.Retry).attempt)
    }

    @Test
    fun noSwitchSettingResendsAPartialStream() {
        // 开关只管「没收到过内容」的情况：吐过半截回答再重发就是内容重复、双花 token
        val on = retryPolicyFor(true).decide(attempt(status = 524, bytes = 1_000L))

        assertTrue("开着也不能盲重半截流", on is RetryDecision.GiveUp)
        assertTrue((on as RetryDecision.GiveUp).reason.contains("1000"))
    }

    @Test
    fun noSwitchSettingResendsContextOverflow() {
        val on = retryPolicyFor(true).decide(
            attempt(status = 524, body = "maximum context length is 8192 tokens"),
        )

        assertEquals(FailureClass.ContextOverflow, (on as RetryDecision.GiveUp).failure)
    }

    @Test
    fun freeDownloadRetriesRegardlessOfTheSwitch() {
        // 免费的、可续传的请求不该被这个开关管住
        val d = retryPolicyFor(false).decide(attempt(status = 522, cost = RequestCost.Free))

        assertTrue("免费请求该自动重来：$d", d is RetryDecision.Retry)
    }

    @Test
    fun flagWritesThroughToTheSettingsFileAndSurvivesRestart() {
        val storage = MemoryStorage()
        val before = stateWith(storage)
        assertFalse(before.flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT))

        before.setFlag(RETRY_COSTLY_KEY, true)
        assertTrue(before.flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT))
        assertNull(before.flushPersistence())

        val after = stateWith(storage)

        assertTrue("重开软件必须还是开的", after.flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT))
        val d = retryPolicyFor(after.autoRetryCostlyEnabled()).decide(attempt(status = 524))
        assertTrue("开关状态得真能决定策略：$d", d is RetryDecision.Retry)
    }

    @Test
    fun flagReadsStoredValueAndFallsBackToCallerDefault() {
        val stored = stateWith(MemoryStorage("#format=1\n$RETRY_COSTLY_KEY=true\n"))
        assertTrue(stored.flag(RETRY_COSTLY_KEY, false))

        val absent = stateWith(MemoryStorage())
        assertFalse(absent.flag(RETRY_COSTLY_KEY, false))
        assertTrue("默认值该由调用方给", absent.flag(RETRY_COSTLY_KEY, true))

        val garbled = stateWith(MemoryStorage("#format=1\n$RETRY_COSTLY_KEY=也许\n"))
        assertFalse("读不懂就用默认值，界面不崩", garbled.flag(RETRY_COSTLY_KEY, false))
    }

    @Test
    fun readingFlagsAloneWritesNothing() {
        val storage = MemoryStorage()
        val state = stateWith(storage)

        repeat(5) { state.flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT) }

        assertNull(state.flushPersistence())
        assertEquals("光读不该写盘", 0, storage.writeCount)
    }

    @Test
    fun nothingIsOrphanedFromTheDemoSections() {
        // 组 id 写错的话真设置项会挂不上任何组：这条红就是在提醒「设置项不见了」
        assertTrue("挂不上组的项：${orphanAdditions().map { it.title }}", orphanAdditions().isEmpty())
        assertTrue(RealSectionAdditions.isNotEmpty())
    }

    @Test
    fun realItemIsAppendedAfterDemoItemsInTheSameGroup() {
        val network = mergedSettingsSections().first { it.id == "s-net" }

        assertEquals("演示项在前、真设置在后", "proxy", network.items.first().subKey)
        assertEquals("retry", network.items.last().subKey)
        assertEquals(2, network.items.size)
    }

    @Test
    fun mergingDoesNotMutateTheDemoTable() {
        val demoCount = SettingsSections.sumOf { it.items.size }

        assertEquals(8, SettingsSections.size)
        // SettingsData 顶部注释写的是「8 组 27 项」，实际条目数由这条钉住：
        // 哪天加删条目，这里的数字和那句注释必须一起改，不许只改一边
        assertEquals("演示表实际条目数（和文件头注释对一次）", 26, demoCount)
        assertEquals(demoCount + 1, mergedSettingsSections().sumOf { it.items.size })
    }

    @Test
    fun subPageLookupPrefersRealTableThenFallsBackToDemo() {
        val retry = subPage("retry")

        assertNotNull(retry)
        assertEquals("失败与重试", retry!!.title)
        assertEquals(SubPages["proxy"], subPage("proxy"))
        assertNull(subPage("no-such-page"))
    }

    @Test
    fun retryPageUsesThePersistedSwitchNotTheDemoOne() {
        val fields = subPage("retry")!!.fields

        val switch = fields.filterIsInstance<SubField.PersistedSwitch>().single()
        assertEquals(RETRY_COSTLY_KEY, switch.key)
        assertEquals(RETRY_COSTLY_DEFAULT, switch.defaultOn)
        assertTrue("演示态 Switch 不该出现在真设置页里", fields.none { it is SubField.Switch })
        assertTrue("得有一句说清代价", fields.filterIsInstance<SubField.Note>().any { it.text.contains("token") })
    }

    @Test
    fun stateWithoutPersistenceStillAnswersFlags() {
        val state = AppUiState()

        assertFalse(state.flag(RETRY_COSTLY_KEY, RETRY_COSTLY_DEFAULT))
        state.setFlag(RETRY_COSTLY_KEY, true)
        assertTrue("没后端时开关照样能用，只是不落盘", state.flag(RETRY_COSTLY_KEY, false))
        assertNull(state.flushPersistence())
    }

    @Test
    fun flushFailureStillReachesTheToggle() {
        val storage = object : SettingsStorage {
            override fun read(): String? = null
            override fun write(text: String) {
                throw IOException("盘满了")
            }
        }
        val state = AppUiState(SettingsUiPersistence(SettingsStore(storage)))
        state.setFlag(RETRY_COSTLY_KEY, true)

        val failure = state.flushPersistence()

        assertNotNull("存不上必须带回原因给 toast", failure)
        assertTrue(failure!!.contains("盘满了"))
    }
}
