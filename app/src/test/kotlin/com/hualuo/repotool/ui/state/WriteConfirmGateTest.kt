package com.hualuo.repotool.ui.state

import com.hualuo.engine.toolcalls.GitHubWriteProposal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 确认闸门的纯 JVM 契约（0.7.0 刀③）：默认拒写、点头放行、拒绝收卡、
 * 超时按拒、单飞防连点。等待线程用真线程跑，时序由 latch 与短超时钉死。
 */
class WriteConfirmGateTest {

    private fun proposal(path: String = "a.txt"): GitHubWriteProposal = GitHubWriteProposal(
        repo = "o/r",
        path = path,
        branch = "main",
        message = "改一下",
        isNewFile = true,
        contentChars = 5,
        contentPreview = "hello",
    )

    @Test
    fun approveLetsTheWriteThrough() {
        val gate = WriteConfirmGate(timeoutMs = 2_000)
        val result = arrayOfNulls<Boolean>(1)
        val worker = Thread { result[0] = gate.confirm(proposal()) }
        worker.start()

        // 卡摆出来后点头（轮询到 pending 出现再点，最长等 1 秒）
        val deadline = System.currentTimeMillis() + 1_000
        while (gate.pending == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
        val card = gate.pending ?: error("提议该摆成卡")
        gate.approve(card.id)
        worker.join(2_000)

        assertEquals(true, result[0])
        assertNull("收卡：点头之后不留悬案", gate.pending)
    }

    @Test
    fun rejectKeepsEverythingUnwritten() {
        val gate = WriteConfirmGate(timeoutMs = 2_000)
        val result = arrayOfNulls<Boolean>(1)
        val worker = Thread { result[0] = gate.confirm(proposal()) }
        worker.start()

        val deadline = System.currentTimeMillis() + 1_000
        while (gate.pending == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
        gate.reject(gate.pending?.id ?: error("提议该摆成卡"))
        worker.join(2_000)

        assertEquals(false, result[0])
        assertNull(gate.pending)
    }

    @Test
    fun timeoutMeansReject() {
        val gate = WriteConfirmGate(timeoutMs = 80)
        val began = System.currentTimeMillis()
        val approved = gate.confirm(proposal())
        val elapsed = System.currentTimeMillis() - began

        assertFalse("等不到点头一律按拒", approved)
        assertTrue("确实等过（不是秒回）：${elapsed}ms", elapsed >= 50)
        assertNull("超时也要收卡", gate.pending)
    }

    @Test
    fun secondProposalWhileOneIsPendingIsRefused() {
        val gate = WriteConfirmGate(timeoutMs = 2_000)
        val firstResult = arrayOfNulls<Boolean>(1)
        val first = Thread { firstResult[0] = gate.confirm(proposal("a.txt")) }
        first.start()

        val deadline = System.currentTimeMillis() + 1_000
        while (gate.pending == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertNotNull(gate.pending)

        // 第一张卡还悬着，第二条提议直接拒（单飞）；原卡不受影响
        assertFalse("单飞：旧卡未决时新提议一律拒", gate.confirm(proposal("b.txt")))
        assertEquals("a.txt", gate.pending?.proposal?.path)

        gate.reject(gate.pending?.id ?: error("卡该还在"))
        first.join(2_000)
        assertEquals(false, firstResult[0])
    }
}
