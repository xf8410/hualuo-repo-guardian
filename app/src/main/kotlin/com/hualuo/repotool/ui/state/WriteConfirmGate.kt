package com.hualuo.repotool.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.toolcalls.GitHubWriteProposal
import com.hualuo.engine.toolcalls.WriteConfirmer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 一张待用户点头的写入确认卡。[id] 防串台：旧卡点不动新提议。 */
class PendingWrite(val id: String, val proposal: GitHubWriteProposal)

/**
 * 写类工具的确认闸门（0.7.0 刀③）：模型提议、用户点头才写。
 *
 * 工作方式：工具执行线程调 [confirm] 时把提议摆成 [pending]（界面据此渲染确认卡），
 * 然后**阻塞等待**——[approve] 放行、[reject] 拒绝、[timeoutMs] 到点按拒。
 * 三条纪律：
 *  - **默认拒写**：等不到明确点头（超时、卡片没人管、调用方没接闸门）全都当拒；
 *  - **单飞**：一张卡没处理完，第二条提议直接拒——不给「连点两下绕确认」留缝；
 *  - **收卡**：无论哪种收场，[pending] 都收走，界面不留悬案。
 *
 * 挂进程（HualuoApplication）不挂 Activity：确认卡渲染期间 Activity 重建（转屏、切出）
 * 也不丢——这与生成域、进度行同一条纪律。
 */
class WriteConfirmGate(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : WriteConfirmer {

    /** 当前待确认的写入；null = 没有悬着的提议。 */
    var pending by mutableStateOf<PendingWrite?>(null)
        private set

    private val lock = Any()
    private var seq = 0
    private var latch: CountDownLatch? = null
    private var approved = false

    /** 摆卡并等决定（阻塞调用线程，最长 [timeoutMs]）。返回 true 才允许真写。 */
    override fun confirm(proposal: GitHubWriteProposal): Boolean {
        val waiter = CountDownLatch(1)
        var myId: String? = null
        synchronized(lock) {
            if (pending == null) {
                seq += 1
                val card = PendingWrite("write-" + seq, proposal)
                myId = card.id
                pending = card
                approved = false
                latch = waiter
            }
        }
        val id = myId ?: return false
        val decided = runCatching { waiter.await(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        synchronized(lock) {
            if (pending?.id == id) pending = null
            if (latch === waiter) latch = null
        }
        return synchronized(lock) { decided && approved }
    }

    /** 用户点头：放行并收卡。 */
    fun approve(id: String) = resolve(id, true)

    /** 用户拒绝：收卡并按拒处理。 */
    fun reject(id: String) = resolve(id, false)

    private fun resolve(id: String, ok: Boolean) {
        synchronized(lock) {
            if (pending?.id != id) return
            approved = ok
            latch?.countDown()
        }
    }

    companion object {
        /** 等确认的上限：5 分钟。等不到就按拒处理，什么都不写。 */
        const val DEFAULT_TIMEOUT_MS = 5 * 60_000L
    }
}
