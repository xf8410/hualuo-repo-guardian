package com.hualuo.repotool.ui.state

import com.hualuo.engine.backup.AgoraImportPlan
import com.hualuo.engine.backup.AgoraSessionPlan
import com.hualuo.engine.store.SessionHead
import com.hualuo.engine.store.StoredMsg
import com.hualuo.repotool.backup.BackupGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 旧 Agora 兑换单应用进活通道的契约（纯 JVM，UiPersistence.None 时 save 是空操作，
 * 这里钉的是「报出来的账」与「键进没进」——persist 是 None，计数逻辑照样走）。
 *
 * 三条已经吃过的亏，钉在这里防再犯：
 *  - app/engine 测试类路径都没有 kotlin-test，用 org.junit（run 35105123539、35107231649）；
 *  - JUnit4 的 assertTrue 是「消息在前、条件在后」，和 kotlin.test 相反——写反了编译器
 *    当成 (Boolean, String) 对不上直接红（run 35108951731）；
 *  - 源码里不许有箭头等符号字符（红线闸门）。
 */
class AgoraImportApplyTest {

    private fun outcome(
        recognized: Boolean = true,
        appliedPlan: AgoraImportPlan? = plan(),
        imported: Int = 2,
        skipped: Int = 1,
        error: String? = null,
    ) = BackupGateway.AgoraImportOutcome(recognized, appliedPlan, imported, skipped, error)

    private fun plan(
        providerName: String? = "deepseek",
        baseUrl: String? = "https://api.deepseek.com/v1",
        apiKey: String? = "sk-live-9",
        selectedModel: String? = "deepseek-chat",
        systemPrompt: String? = "你是账房先生。",
        notes: List<String> = listOf("定时任务 1 条、循环 0 条：新版还没有这功能，没导（已在路线图）"),
    ) = AgoraImportPlan(
        recognized = true,
        formatVersion = 3,
        providerName = providerName,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        thinkingOn = true,
        thinkingLevel = 3,
        codeExecOn = false,
        webSearchOn = true,
        shellOn = false,
        systemPrompt = systemPrompt,
        sessions = listOf(
            AgoraSessionPlan(
                "agora-x",
                SessionHead("挖矿记录", "deepseek-chat", 1700000000000),
                listOf(StoredMsg(StoredMsg.ROLE_USER, "开矿", 1700000001000)),
            ),
        ),
        notes = notes,
    )

    @Test
    fun recognizedPlanReportsCountsAndNotes() {
        val state = AppUiState()
        val message = state.applyAgoraImport(outcome())
        assertTrue(message, message.contains("旧 Agora 备份导入完成"))
        assertTrue(message, message.contains("会话 2 份"))
        assertTrue(message, message.contains("重名会话跳过 1 份"))
        assertTrue(message, message.contains("定时任务 1 条"))
    }

    @Test
    fun unrecognizedPlanImportsNothingAndSaysWhy() {
        val state = AppUiState()
        val message = state.applyAgoraImport(
            outcome(recognized = false, appliedPlan = null, error = "不是旧 Agora 的备份包"),
        )
        assertEquals("不是旧 Agora 的备份包", message)
    }

    @Test
    fun emptyFieldsAreCountedAsNotAppliedRatherThanOverwriting() {
        val state = AppUiState()
        // 没兑出密钥/base URL/系统指令：不写空串盖旧值（旧值是用户手填的，兑换不许清它）
        val message = state.applyAgoraImport(
            outcome(appliedPlan = plan(baseUrl = null, apiKey = null, systemPrompt = null, notes = emptyList())),
        )
        assertTrue(message, message.contains("设置 7 项"))
        assertTrue(message, !message.contains("；；"))
    }
}
