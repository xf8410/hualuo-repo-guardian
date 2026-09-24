package com.hualuo.repotool

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.UiPersistenceBundle
import com.hualuo.repotool.ui.state.WriteConfirmGate
import com.hualuo.repotool.ui.state.createUiPersistence
import java.io.File

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

    /**
     * 写类工具的确认闸门（0.7.0 刀③）：模型提议改仓库时摆确认卡，用户点头才写。
     * 挂进程不挂界面——确认卡渲染期间 Activity 重建（转屏/切出）不丢；
     * 与 uiState 同源注入（ToolWiring 只有拿到它才注册写工具，默认拒写）。
     */
    val writeGate = WriteConfirmGate()

    /** 全部界面状态。全进程一份；Activity 重建只是重新接上它。 */
    val uiState: AppUiState by lazy {
        AppUiState(uiBundle.persistence, uiBundle.store, writeGate, memoryStore)
    }

    /**
     * 记忆库（M4 第二刀的 app 侧挂载）：memory_db 目录建不起来就 null 降级——
     * 记忆工具族不注册（模型碰不到），聊天照常。路径与旧 Agora 一致：
     * filesDir/memory_db + filesDir/active_memory.md。
     */
    private val memoryStore: com.hualuo.engine.memory.MemoryStore? by lazy {
        try {
            com.hualuo.engine.memory.MemoryStore(
                memoryDir = File(filesDir, "memory_db"),
                activeFile = File(filesDir, "active_memory.md"),
            )
        } catch (e: Exception) {
            null
        }
    }

    private val startupNotice = OnceNotice { uiBundle.notice }

    /** 启动通知取走即没：进程活着时只出一次声，重建不再复读。 */
    fun consumeStartupNotice(): String? = startupNotice.consume()

    /**
     * 长活进度行（0.6.0）：「正在打包 2/7 份会话」「已读 5 份」这类。
     * 挂进程不挂界面——备份跑几分钟，Activity 重建（转屏/切出）进度行不许丢。
     * 备份线程写、界面读（Compose 快照线程安全）；动作收尾必须写 null 收行，
     * 不许挂着上一次的旧账骗人。
     */
    var backupProgress by mutableStateOf<String?>(null)

    /**
     * 长活进度行（文件投递，0.7.0 同款接法）：「正在收集 12 个文件」「正在投递卷 3/5」这类。
     * 与 backupProgress 同一条道理：投递跑几分钟，Activity 重建（转屏/切出）进度行不许丢；
     * 投递线程写、界面读；收尾必须写 null 收行，不许挂着上一次的旧账骗人。
     */
    var courierProgress by mutableStateOf<String?>(null)
}
