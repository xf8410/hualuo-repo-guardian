package com.hualuo.repotool

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hualuo.engine.api.ProviderRouting
import com.hualuo.repotool.ui.state.AppUiState
import com.hualuo.repotool.ui.state.ModelSettingsRuntime
import com.hualuo.repotool.ui.state.ModelSettingsState
import com.hualuo.repotool.ui.state.UiPersistenceBundle
import com.hualuo.repotool.ui.state.WriteConfirmGate
import com.hualuo.repotool.ui.state.createUiPersistence
import java.io.File

/**
 * 进程级内核：设置出口、会话仓、全部界面状态都住这里，不归任何一任 Activity 管。
 */
class HualuoApplication : Application() {

    val uiBundle: UiPersistenceBundle by lazy { createUiPersistence(this) }

    /** 多提供商模型设置的全进程句柄；设置页与聊天发送共用这一份事实。 */
    val modelSettings: ModelSettingsState by lazy {
        ModelSettingsState(uiBundle.persistence, changed = { uiBundle.persistence.flush() }).also {
            ModelSettingsRuntime.install(it)
            ProviderRouting.install(it::sessionFor)
        }
    }

    val writeGate = WriteConfirmGate()

    /** 记忆库：建不起来就 null，记忆工具不注册，聊天仍照常。 */
    private val memoryStore: com.hualuo.engine.memory.MemoryStore? by lazy {
        try {
            com.hualuo.engine.memory.MemoryStore(
                memoryDir = File(filesDir, "memory_db"),
                activeFile = File(filesDir, "active_memory.md"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 技能库（M4 第六刀）：skill_db，无活动记忆文件；建不起来 null 降级。 */
    private val skillStore: com.hualuo.engine.memory.MemoryStore? by lazy {
        try {
            com.hualuo.engine.memory.MemoryStore(memoryDir = File(filesDir, "skill_db"))
        } catch (_: Exception) {
            null
        }
    }

    val uiState: AppUiState by lazy {
        AppUiState(uiBundle.persistence, uiBundle.store, writeGate, memoryStore, skillStore)
    }

    private val startupNotice = OnceNotice { uiBundle.notice }
    fun consumeStartupNotice(): String? = startupNotice.consume()

    var backupProgress by mutableStateOf<String?>(null)
    var courierProgress by mutableStateOf<String?>(null)

    init {
        modelSettings
    }
}

class OnceNotice(private val provide: () -> String?) {
    private var consumed = false
    fun consume(): String? = if (consumed) null else provide()?.also { consumed = true }
}
