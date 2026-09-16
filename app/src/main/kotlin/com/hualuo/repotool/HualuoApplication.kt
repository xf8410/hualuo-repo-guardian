package com.hualuo.repotool

import android.app.Application
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.UiPersistenceBundle
import com.hualuo.repotool.ui.state.createUiPersistence

/**
 * 「只许响一次」的通知：进程活着时，无论 Activity 死几次，同一句话只出一次声。
 * 纯 JVM 可测——进程级状态的配套小件。
 */
class OnceNotice(private val provide: () -> String?) {
    private var consumed = false

    /** 第一次拿到非空就出声并记账；之后（含 Activity 重建）一律 null。空通知不记账。 */
    fun consume(): String? = if (consumed) null else provide()?.also { consumed = true }
}

/**
 * 进程级内核：设置出口、会话仓、全部界面状态都住这里，**不归任何一任 Activity 管**。
 *
 * 为什么必须存在（run 35097435110 之后的病根，RootScreen 里 remember 一行实锤）：
 * AppUiState 挂在组合域时，切出 App（回桌面、转屏、内存回收）会让 Activity 重建、
 * remember 重跑——**整个状态换新实例**：生成中的流被孤立、忙灯归零、半截话没人管。
 * 「切换出去对话就停了/生成失败」就是这个病（旧 Agora 同病）。
 *
 * 治法：状态挂在 Application 上，Activity 随便死——进程活着，生成协程（后台线程）
 * 就在跑；切回来拿到的是**同一个** AppUiState，半截话、忙灯、账本原样接上。
 * 红线不碰：没有前台服务，进程真被系统杀（后台太久）时靠会话库的落盘保底，
 * 那是「少最后一句话」不是「整个会话崩了」。
 *
 * uiBundle/uiState 都是 lazy：首次访问才建，且全进程只建一次——
 * 两份事实的老病（两个 SettingsStore 各写各的）从构造上堵死。
 */
class HualuoApplication : Application() {

    /** 设置出口 + 会话仓 + 启动通知（读不懂才非空）。全进程一份。 */
    val uiBundle: UiPersistenceBundle by lazy { createUiPersistence(this) }

    /** 全部界面状态。全进程一份；Activity 重建只是重新接上它。 */
    val uiState: AppUiState by lazy { AppUiState(uiBundle.persistence, uiBundle.store) }

    private val startupNotice = OnceNotice { uiBundle.notice }

    /** 启动通知取走即没：进程活着时只出一次声，重建不再复读。 */
    fun consumeStartupNotice(): String? = startupNotice.consume()
}
