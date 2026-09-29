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
    // ── 事件观测流（560 清单 398；游标增量拉，育成 AI 主线数据） ──

    /** 流水行（原文 JSON，零脱敏；尽力抽 id 前缀，解析失败原文照显）。 */
    var eventRows by mutableStateOf<List<String>>(emptyList())
        private set
    var eventCursor by mutableStateOf(0L)
        private set
    var eventNote by mutableStateOf<String?>(null)
        private set
    var eventBusy by mutableStateOf(false)
        private set

    /** 增量拉事件观测：after_id=上次最大 id。原文进行列表，游标推进。 */
    fun pullEvents() {
        if (eventBusy) return
        val client = observeClient() ?: run { eventNote = "桥不在：先探测"; return }
        eventBusy = true
        Thread {
            try {
                val out = client.get(ObserveClient.ENDPOINT_EVENT_OBSERVATIONS + "?after_id=$eventCursor")
                if (!out.ok) {
                    eventNote = out.httpStatus?.let { ObserveClient.httpExplain(it) } ?: (out.error ?: "无响应")
                    return@Thread
                }
                val body = out.body ?: ""
                var maxId = eventCursor
                val rows = ArrayList<String>()
                // 轻抽：正则抓每条事件的 id/type（避免 app 模块引 JSON 依赖）；
                // 抓不到就整段原文照显——形状不合已知事件数组时不猜
                val idRegex = Regex("\"id\"\\s*:\\s*(\\d+)")
                val typeRegex = Regex("\"type\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                val objects = body.split("},")
                for (obj in objects) {
                    val id = idRegex.find(obj)?.groupValues?.get(1)?.toLongOrNull() ?: continue
                    if (id > maxId) maxId = id
                    val type = typeRegex.find(obj)?.groupValues?.get(1) ?: ""
                    rows += (if (id > 0) "#$id " else "") + (if (type.isNotBlank()) "[$type] " else "") + obj.trim().take(600)
                }
                if (rows.isEmpty()) {
                    eventRows = listOf(body.take(2000))
                    eventNote = "对端回了原文（形状不是已知事件数组），照显不猜"
                } else {
                    eventRows = rows.take(200)
                    eventNote = "拉到 ${rows.size} 条（游标 $eventCursor 到 $maxId）"
                    eventCursor = maxId
                }
            } catch (e: Exception) {
                eventNote = "拉取失败：${e.message ?: e::class.java.simpleName}"
            } finally {
                eventBusy = false
            }
        }.start()
    }

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
