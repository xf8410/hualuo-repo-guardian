package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.api.UrlConnTransport
import com.hualuo.engine.observe.ObserveClient
import com.hualuo.engine.observe.ObserveState

/**
 * SO 观测桥状态舱（560 清单 361-400 域，红线三拆件——AppUiState 只持有一行）。
 *
 * 持有连接状态机 [link]（六态/单并发/退避冷却都在引擎件里）与桥地址；
 * 探测编排在这开后台线程跑（health 到 status 再判态），UI 只读快照。
 * 地址现读现用（observeClient 每次现造）：设置页改地址下一发就生效。
 *
 * 红线：全只读（361-400 域纪律）——本舱只发 GET，18767 端口冻结不碰。
 */
class ObserveUiState(
    private val persist: UiPersistence,
) {

    val link = ObserveState()

    /** 桥地址（可改，持久化）。默认本机回环——hlpatch SO 跑在同一台设备上。 */
    var baseUrl: String = persist.load(UiKeys.OBSERVE_BASE)?.ifBlank { null } ?: DEFAULT_BASE
        set(value) {
            val v = value.trim()
            if (field == v) return
            field = v
            persist.save(UiKeys.OBSERVE_BASE, v)
        }

    var probing by mutableStateOf(false)
        private set

    /** 探测收场一句话（错误原因/成功档位），UI 显示用。 */
    var probeNote by mutableStateOf<String?>(null)
        private set

    /** 探测拿到的 /health、/status 原文（零脱敏照存，UI 滚动展示）。 */
    var healthBody by mutableStateOf<String?>(null)
        private set
    var statusBody by mutableStateOf<String?>(null)
        private set

    /** 现造一个客户端：地址此刻值 + JDK transport。地址非法（无 scheme）返回 null。 */
    fun observeClient(): ObserveClient? {
        val base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) return null
        return ObserveClient(base, UrlConnTransport())
    }

    /**
     * 探测编排（后台线程）：health 到 status 再状态机判态。
     * 进行中再点无效（单并发把门在 [ObserveState]，这里只是 UI 灯）。
     */
    fun probe() {
        if (probing) return
        probing = true
        probeNote = null
        Thread {
            try {
                val client = observeClient()
                if (client == null) {
                    probeNote = "地址不对：要 http:// 或 https:// 开头"
                    return@Thread
                }
                if (!link.tryBeginProbe()) {
                    probeNote = link.lastNote
                    return@Thread
                }
                val outcome = client.probe()
                healthBody = outcome.health?.takeIf { it.ok }?.body
                statusBody = outcome.status?.takeIf { it.ok }?.body
                link.settleProbe(
                    healthOk = outcome.health?.ok == true,
                    statusOk = outcome.status?.ok == true,
                    healthStatus = outcome.health?.takeIf { it.ok }?.httpStatus,
                    note = probeSummary(outcome),
                )
                probeNote = probeSummary(outcome)
            } catch (e: Exception) {
                link.settleProbe(
                    healthOk = false, statusOk = false, healthStatus = null,
                    note = "探测异常 ${e.message ?: e::class.java.simpleName}",
                )
                probeNote = "探测异常 ${e.message ?: e::class.java.simpleName}"
            } finally {
                probing = false
            }
        }.start()
    }

    /** 一句话探测账：读数给 UI，不带情绪。 */
    private fun probeSummary(outcome: ObserveClient.ProbeOutcome): String {
        val h = outcome.health
        val s = outcome.status
        return when {
            h == null -> "无响应（18765 没回话——游戏没开或插件没挂上）"
            !h.ok -> "health 失败：${h.error ?: "HTTP ${h.httpStatus}"}"
            s == null -> "疑似非 hlpatch（health 原文不是 JSON）"
            !s.ok -> "降级：health 通，status 失败（${s.error ?: "HTTP ${s.httpStatus}"}）"
            else -> "已就绪（health + status 都通）"
        }
    }

    companion object {
        /** hlpatch SO 观测桥默认口：本机回环（游戏与 app 同机，避开 VPN/代理）。 */
        const val DEFAULT_BASE = "http://127.0.0.1:18765"
    }
}
