package com.hualuo.repotool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「只许响一次」通知的契约：进程级状态的配套小件，纯 JVM 可测。 */
class OnceNoticeTest {

    @Test
    fun noticeShowsExactlyOnceAcrossRecreates() {
        var asked = 0
        val once = OnceNotice { asked++; "设置里有读不懂的项" }
        assertEquals("设置里有读不懂的项", once.consume())
        assertNull("第二次（重建后）必须安静", once.consume())
        assertNull(once.consume())
        assertEquals(1, asked)
    }

    @Test
    fun emptyNoticeNeverMarksConsumed() {
        var asked = 0
        val once = OnceNotice { asked++; null }
        assertNull(once.consume())
        assertNull(once.consume())
        assertEquals(2, asked)
    }
}
